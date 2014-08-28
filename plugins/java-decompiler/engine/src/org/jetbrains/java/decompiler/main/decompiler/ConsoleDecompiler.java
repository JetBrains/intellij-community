/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.java.decompiler.main.decompiler.helper.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IDecompilatSaver;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


public class ConsoleDecompiler implements IBytecodeProvider, IDecompilatSaver {

  private File root;

  private Fernflower fernflower;

  private HashMap<String, ZipOutputStream> mapArchiveStreams = new HashMap<String, ZipOutputStream>();

  private HashMap<String, HashSet<String>> mapArchiveEntries = new HashMap<String, HashSet<String>>();

  public ConsoleDecompiler() {
    this(null);
  }

  public ConsoleDecompiler(HashMap<String, Object> propertiesCustom) {
    this(new PrintStreamLogger(IFernflowerLogger.WARNING, System.out), propertiesCustom);
  }

  protected ConsoleDecompiler(IFernflowerLogger logger, HashMap<String, Object> propertiesCustom) {
    fernflower = new Fernflower(this, this, propertiesCustom);
    DecompilerContext.setLogger(logger);
  }

  public static void main(String[] args) {

    try {

      if (args != null && args.length > 1) {

        HashMap<String, Object> mapOptions = new HashMap<String, Object>();

        List<String> lstSources = new ArrayList<String>();
        List<String> lstLibraries = new ArrayList<String>();

        boolean isOption = true;
        for (int i = 0; i < args.length - 1; ++i) { // last parameter - destination
          String arg = args[i];

          if (isOption && arg.startsWith("-") &&
              arg.length() > 5 && arg.charAt(4) == '=') {
            String value = arg.substring(5).toUpperCase();
            if ("TRUE".equals(value)) {
              value = "1";
            }
            else if ("FALSE".equals(value)) {
              value = "0";
            }

            mapOptions.put(arg.substring(1, 4), value);
          }
          else {
            isOption = false;

            if (arg.startsWith("-e=")) {
              lstLibraries.add(arg.substring(3));
            }
            else {
              lstSources.add(arg);
            }
          }
        }

        if (lstSources.isEmpty()) {
          printHelp();
        }
        else {
          ConsoleDecompiler decompiler = new ConsoleDecompiler(
            new PrintStreamLogger(IFernflowerLogger.INFO, System.out),
            mapOptions);

          for (String source : lstSources) {
            decompiler.addSpace(new File(source), true);
          }

          for (String library : lstLibraries) {
            decompiler.addSpace(new File(library), false);
          }

          decompiler.decompileContext(new File(args[args.length - 1]));
        }
      }
      else {
        printHelp();
      }
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private static void printHelp() {
    System.out.println("Usage: java ConsoleDecompiler ( -<option>=<value>)* (<source>)+ <destination>");
    System.out.println("Example: java ConsoleDecompiler -dgs=true c:\\mysource\\ c:\\my.jar d:\\decompiled\\");
  }

  public void addSpace(File file, boolean isOwn) throws IOException {
    fernflower.getStructcontext().addSpace(file, isOwn);
  }

  public void decompileContext(File root) {
    this.root = root;
    fernflower.decompileContext();
  }

  // *******************************************************************
  // Interface IBytecodeProvider
  // *******************************************************************

  public InputStream getBytecodeStream(String externPath, String internPath) {

    try {
      File file = new File(externPath);

      if (internPath == null) {
        return new FileInputStream(file);
      }
      else { // archive file
        ZipFile archive = new ZipFile(file);

        Enumeration<? extends ZipEntry> en = archive.entries();
        while (en.hasMoreElements()) {
          ZipEntry entr = en.nextElement();

          if (entr.getName().equals(internPath)) {
            return archive.getInputStream(entr);
          }
        }
      }
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }

    return null;
  }

  // *******************************************************************
  // Interface IDecompilatSaver
  // *******************************************************************

  private String getAbsolutePath(String path) {
    return new File(root, path).getAbsolutePath();
  }

  private boolean addEntryName(String filename, String entry) {
    HashSet<String> set = mapArchiveEntries.get(filename);
    if (set == null) {
      mapArchiveEntries.put(filename, set = new HashSet<String>());
    }

    return set.add(entry);
  }

  public void copyEntry(String source, String destpath, String archivename, String entryName) {

    try {
      String filename = new File(getAbsolutePath(destpath), archivename).getAbsolutePath();

      if (!addEntryName(filename, entryName)) {
        DecompilerContext.getLogger().writeMessage("Zip entry already exists: " +
                                                   destpath + "," + archivename + "," + entryName, IFernflowerLogger.WARNING);
        return;
      }

      ZipFile srcarchive = new ZipFile(new File(source));

      Enumeration<? extends ZipEntry> en = srcarchive.entries();
      while (en.hasMoreElements()) {
        ZipEntry entr = en.nextElement();

        if (entr.getName().equals(entryName)) {
          InputStream in = srcarchive.getInputStream(entr);

          ZipOutputStream out = mapArchiveStreams.get(filename);
          out.putNextEntry(new ZipEntry(entryName));

          InterpreterUtil.copyInputStream(in, out);
          in.close();
        }
      }

      srcarchive.close();
    }
    catch (IOException ex) {
      DecompilerContext.getLogger()
        .writeMessage("Error copying zip file entry: " + source + "," + destpath + "," + archivename + "," + entryName,
                      IFernflowerLogger.WARNING);
      ex.printStackTrace();
    }
  }

  public void copyFile(String source, String destpath, String destfilename) {
    try {
      InterpreterUtil.copyFile(new File(source), new File(destfilename));
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public void saveFile(String path, String filename, String content) {
    try {
      BufferedWriter out =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(getAbsolutePath(path), filename)), "UTF8"));
      out.write(content);
      out.flush();
      out.close();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public void createArchive(String path, String archivename, Manifest manifest) {

    try {
      File file = new File(getAbsolutePath(path), archivename);
      file.createNewFile();

      ZipOutputStream out;
      if (manifest != null) { // jar
        out = new JarOutputStream(new FileOutputStream(file), manifest);
      }
      else {
        out = new ZipOutputStream(new FileOutputStream(file));
      }
      mapArchiveStreams.put(file.getAbsolutePath(), out);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public void saveClassEntry(String path, String archivename,
                             String qualifiedName, String entryName, String content) {
    saveEntry(path, archivename, entryName, content);
  }

  public void saveClassFile(String path, String qualifiedName, String entryName, String content) {
    saveFile(path, entryName, content);
  }

  public void saveEntry(String path, String archivename, String entryName,
                        String content) {

    try {
      String filename = new File(getAbsolutePath(path), archivename).getAbsolutePath();

      if (!addEntryName(filename, entryName)) {
        DecompilerContext.getLogger().writeMessage("Zip entry already exists: " +
                                                   path + "," + archivename + "," + entryName, IFernflowerLogger.WARNING);
        return;
      }

      ZipOutputStream out = mapArchiveStreams.get(filename);
      out.putNextEntry(new ZipEntry(entryName));

      if (content != null) {
        BufferedWriter outwriter = new BufferedWriter(new OutputStreamWriter(out, "UTF8"));
        outwriter.write(content);
        outwriter.flush();
      }
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  public void saveFolder(String path) {
    File f = new File(getAbsolutePath(path));
    f.mkdirs();
  }


  public void closeArchive(String path, String archivename) {
    try {
      String filename = new File(getAbsolutePath(path), archivename).getAbsolutePath();

      mapArchiveEntries.remove(filename);
      ZipOutputStream out = mapArchiveStreams.remove(filename);

      out.flush();
      out.close();
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }
}
