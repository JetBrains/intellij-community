// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.lang.ClassPath;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Random;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

@ApiStatus.Internal
public final class CommandLineWrapperUtil {
  public static final String CLASSPATH_JAR_FILE_NAME_PREFIX = ClassPath.CLASSPATH_JAR_FILE_NAME_PREFIX;

  public static @NotNull File createClasspathJarFile(@NotNull Manifest manifest, @NotNull List<String> pathList) throws IOException {
    File file = FileUtil.createTempFile(CLASSPATH_JAR_FILE_NAME_PREFIX + Math.abs(new Random().nextInt(Integer.MAX_VALUE)), ".jar", true);
    fillClasspathJarFile(manifest, pathList, false, file);
    return file;
  }

  public static void fillClasspathJarFile(Manifest manifest, List<String> pathList, boolean notEscape, @NotNull File outputJar)
    throws IOException {
    StringBuilder classPath = new StringBuilder();
    for (String path : pathList) {
      if (classPath.length() > 0) classPath.append(' ');
      File classpathElement = new File(path);
      //noinspection deprecation
      String url = (notEscape ? classpathElement.toURL() : classpathElement.toURI().toURL()).toString();
      classPath.append(url);
    }
    fillClasspathJarFile(manifest, classPath.toString(), outputJar);
  }

  public static void fillClasspathJarFile(Manifest manifest, String classPath, @NotNull File outputJar)
    throws IOException {
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
    //noinspection IOResourceOpenedButNotSafelyClosed
    new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputJar)), manifest).close();
  }

  public static @NotNull File createArgumentFile(@NotNull List<String> args, @NotNull Charset cs) throws IOException {
    File argFile = FileUtil.createTempFile("idea_arg_file" + new Random().nextInt(Integer.MAX_VALUE), null, true);
    writeArgumentsFile(argFile, args, cs);
    return argFile;
  }

  /**
   * Writes list of Java arguments to the Java Command-Line Argument File
   * See https://docs.oracle.com/javase/9/tools/java.htm, section "java Command-Line Argument Files"
   *
   * @param argFile a file to write arguments into
   * @param args    arguments
   * @param cs      a character encoding of an output file, must be ASCII-compatible (e.g. UTF-8)
   */
  public static void writeArgumentsFile(@NotNull File argFile, @NotNull List<String> args, @NotNull Charset cs) throws IOException {
    writeArgumentsFile(argFile, args, System.lineSeparator(), cs);
  }

  /**
   * Writes list of Java arguments to the Java Command-Line Argument File
   * See https://docs.oracle.com/javase/9/tools/java.htm, section "java Command-Line Argument Files"
   *
   * @param argFile       a file to write arguments into
   * @param args          arguments
   * @param lineSeparator a line separator to use in file
   * @param cs            a character encoding of an output file, must be ASCII-compatible (e.g. UTF-8)
   */
  public static void writeArgumentsFile(@NotNull File argFile,
                                        @NotNull List<String> args,
                                        String lineSeparator,
                                        @NotNull Charset cs) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(argFile), cs))) {
      for (String arg : args) {
        writer.write(quoteArg(arg));
        writer.write(lineSeparator);
      }
    }
  }

  private static String quoteArg(String arg) {
    String specials = " #'\"\n\r\t\f";
    if (!StringUtil.containsAnyChar(arg, specials)) {
      return arg;
    }

    @NonNls StringBuilder sb = new StringBuilder(arg.length() * 2);
    for (int i = 0; i < arg.length(); i++) {
      char c = arg.charAt(i);
      if (c == ' ' || c == '#' || c == '\'') sb.append('"').append(c).append('"');
      else if (c == '"') sb.append("\"\\\"\"");
      else if (c == '\n') sb.append("\"\\n\"");
      else if (c == '\r') sb.append("\"\\r\"");
      else if (c == '\t') sb.append("\"\\t\"");
      else if (c == '\f') sb.append("\"\\f\"");
      else sb.append(c);
    }
    return sb.toString();
  }

  public static @NotNull File createWrapperFile(@NotNull List<String> classpath, @NotNull Charset cs) throws IOException {
    File file = FileUtil.createTempFile("classpath" + new Random().nextInt(Integer.MAX_VALUE), null, true);
    writeWrapperFile(file, classpath, System.lineSeparator(), cs);
    return file;
  }

  public static void writeWrapperFile(@NotNull File wrapperFile,
                                      @NotNull List<String> classpath,
                                      @NotNull String lineSeparator,
                                      @NotNull Charset cs) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(wrapperFile), cs))) {
      for (String path : classpath) {
        writer.write(path);
        writer.write(lineSeparator);
      }
    }
  }
}