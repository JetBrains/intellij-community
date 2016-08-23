/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ConsoleDecompiler implements IBytecodeProvider, IResultSaver {

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    if (args.length < 2) {
      System.out.println(
        "Usage: java -jar fernflower.jar [-<option>=<value>]* [<source>]+ <destination>\n" +
        "Example: java -jar fernflower.jar -dgs=true c:\\my\\source\\ c:\\my.jar d:\\decompiled\\");
      return;
    }

    Map<String, Object> mapOptions = new HashMap<>();
    List<File> lstSources = new ArrayList<>();
    List<File> lstLibraries = new ArrayList<>();

    boolean isOption = true;
    for (int i = 0; i < args.length - 1; ++i) { // last parameter - destination
      String arg = args[i];

      if (isOption && arg.length() > 5 && arg.charAt(0) == '-' && arg.charAt(4) == '=') {
        String value = arg.substring(5);
        if ("true".equalsIgnoreCase(value)) {
          value = "1";
        }
        else if ("false".equalsIgnoreCase(value)) {
          value = "0";
        }

        mapOptions.put(arg.substring(1, 4), value);
      }
      else {
        isOption = false;

        if (arg.startsWith("-e=")) {
          addPath(lstLibraries, arg.substring(3));
        }
        else {
          addPath(lstSources, arg);
        }
      }
    }

    if (lstSources.isEmpty()) {
      System.out.println("error: no sources given");
      return;
    }

    File destination = new File(args[args.length - 1]);
    if (!destination.isDirectory()) {
      System.out.println("error: destination '" + destination + "' is not a directory");
      return;
    }

    PrintStreamLogger logger = new PrintStreamLogger(System.out);
    ConsoleDecompiler decompiler = new ConsoleDecompiler(destination, mapOptions, logger);

    for (File source : lstSources) {
      decompiler.addSpace(source, true);
    }
    for (File library : lstLibraries) {
      decompiler.addSpace(library, false);
    }

    decompiler.decompileContext();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void addPath(List<File> list, String path) {
    File file = new File(path);
    if (file.exists()) {
      list.add(file);
    }
    else {
      System.out.println("warn: missing '" + path + "', ignored");
    }
  }

  // *******************************************************************
  // Implementation
  // *******************************************************************

  private final File root;
  private final Fernflower fernflower;
  private final Map<String, ZipOutputStream> mapArchiveStreams = new HashMap<>();
  private final Map<String, Set<String>> mapArchiveEntries = new HashMap<>();

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public ConsoleDecompiler(File destination, Map<String, Object> options) {
    this(destination, options, new PrintStreamLogger(System.out));
  }

  protected ConsoleDecompiler(File destination, Map<String, Object> options, IFernflowerLogger logger) {
    root = destination;
    fernflower = new Fernflower(this, this, options, logger);
  }

  public void addSpace(File file, boolean isOwn) {
    fernflower.getStructContext().addSpace(file, isOwn);
  }

  public void decompileContext() {
    try {
      fernflower.decompileContext();
    }
    finally {
      fernflower.clearContext();
    }
  }

  // *******************************************************************
  // Interface IBytecodeProvider
  // *******************************************************************

  @Override
  public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
    File file = new File(externalPath);
    if (internalPath == null) {
      return InterpreterUtil.getBytes(file);
    }
    else {
      try (ZipFile archive = new ZipFile(file)) {
        ZipEntry entry = archive.getEntry(internalPath);
        if (entry == null) throw new IOException("Entry not found: " + internalPath);
        return InterpreterUtil.getBytes(archive, entry);
      }
    }
  }

  // *******************************************************************
  // Interface IResultSaver
  // *******************************************************************

  private String getAbsolutePath(String path) {
    return new File(root, path).getAbsolutePath();
  }

  @Override
  public void saveFolder(String path) {
    File dir = new File(getAbsolutePath(path));
    if (!(dir.mkdirs() || dir.isDirectory())) {
      throw new RuntimeException("Cannot create directory " + dir);
    }
  }

  @Override
  public void copyFile(String source, String path, String entryName) {
    try {
      InterpreterUtil.copyFile(new File(source), new File(getAbsolutePath(path), entryName));
    }
    catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot copy " + source + " to " + entryName, ex);
    }
  }

  @Override
  public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
    File file = new File(getAbsolutePath(path), entryName);
    try (Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF8")) {
      out.write(content);
    }
    catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot write class file " + file, ex);
    }
  }

  @Override
  public void createArchive(String path, String archiveName, Manifest manifest) {
    File file = new File(getAbsolutePath(path), archiveName);
    try {
      if (!(file.createNewFile() || file.isFile())) {
        throw new IOException("Cannot create file " + file);
      }

      FileOutputStream fileStream = new FileOutputStream(file);
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      ZipOutputStream zipStream = manifest != null ? new JarOutputStream(fileStream, manifest) : new ZipOutputStream(fileStream);
      mapArchiveStreams.put(file.getPath(), zipStream);
    }
    catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot create archive " + file, ex);
    }
  }

  @Override
  public void saveDirEntry(String path, String archiveName, String entryName) {
    saveClassEntry(path, archiveName, null, entryName, null);
  }

  @Override
  public void copyEntry(String source, String path, String archiveName, String entryName) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();

    if (!checkEntry(entryName, file)) {
      return;
    }

    try (ZipFile srcArchive = new ZipFile(new File(source))) {
      ZipEntry entry = srcArchive.getEntry(entryName);
      if (entry != null) {
        try (InputStream in = srcArchive.getInputStream(entry)) {
          ZipOutputStream out = mapArchiveStreams.get(file);
          out.putNextEntry(new ZipEntry(entryName));
          InterpreterUtil.copyStream(in, out);
        }
      }
    }
    catch (IOException ex) {
      String message = "Cannot copy entry " + entryName + " from " + source + " to " + file;
      DecompilerContext.getLogger().writeMessage(message, ex);
    }
  }

  @Override
  public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();

    if (!checkEntry(entryName, file)) {
      return;
    }

    try {
      ZipOutputStream out = mapArchiveStreams.get(file);
      out.putNextEntry(new ZipEntry(entryName));
      if (content != null) {
        out.write(content.getBytes("UTF-8"));
      }
    }
    catch (IOException ex) {
      String message = "Cannot write entry " + entryName + " to " + file;
      DecompilerContext.getLogger().writeMessage(message, ex);
    }
  }

  private boolean checkEntry(String entryName, String file) {
    Set<String> set = mapArchiveEntries.get(file);
    if (set == null) {
      mapArchiveEntries.put(file, set = new HashSet<>());
    }

    boolean added = set.add(entryName);
    if (!added) {
      String message = "Zip entry " + entryName + " already exists in " + file;
      DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
    }
    return added;
  }

  @Override
  public void closeArchive(String path, String archiveName) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();
    try {
      mapArchiveEntries.remove(file);
      mapArchiveStreams.remove(file).close();
    }
    catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot close " + file, IFernflowerLogger.Severity.WARN);
    }
  }
}