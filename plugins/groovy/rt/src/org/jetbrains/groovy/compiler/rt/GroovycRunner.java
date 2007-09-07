/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.groovy.compiler.rt;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.messages.WarningMessage;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */

public class GroovycRunner {
  static CompilationUnitsFactory myFactory = new CompilationUnitsFactory();

  public static final String CLASSPATH = "classpath";
  public static final String OUTPUTPATH = "outputpath";
  public static final String TEST_OUTPUTPATH = "test_outputpath";
  public static final String MODULE_NAME = "moduleName";
  public static final String SRC_FOLDERS = "srcFolders";
  public static final String TEST_FILE = "test_file";
  public static final String SRC_FILE = "src_file";

  public static final String COMPILED_START = "%%c";
  public static final String COMPILED_END = "/%c";

  public static final String TO_RECOMPILE_START = "%%rc";
  public static final String TO_RECOMPILE_END = "/%rc";

  public static final String MESSAGES_START = "%%m";
  public static final String MESSAGES_END = "/%m";

  public static final String SEPARATOR = "%$%";

  public static void main(String[] args) {
    String moduleClasspath = null;
    String moduleOutputPath = null;
    String moduleTestOutputPath = null;
    String moduleName = null;
    String moduleSourceFoldersPath = null;

    if (args.length != 1) {
      System.err.println("There is no arguments for groovy compiler");
      return;
    }

    File argsFile = new File(args[0]);

    if (!argsFile.exists()) {
      System.err.println("Arguments file for groovy compiler not found");
      return;
    }

    List<File> srcFiles = new ArrayList<File>();
    List<File> testFiles = new ArrayList<File>();

    BufferedReader reader = null;
    FileInputStream stream;

    try {
      stream = new FileInputStream(argsFile);
      reader = new BufferedReader(new InputStreamReader(stream));

      String line;

      while((line = reader.readLine()) != null && !line.equals(CLASSPATH)) {
        if (TEST_FILE.equals(line)) testFiles.add(new File(reader.readLine()));
        else
        if (SRC_FILE.equals(line)) srcFiles.add(new File(reader.readLine()));
      }

      while (line != null) {
        if (line.startsWith(CLASSPATH)) {
          moduleClasspath = reader.readLine();
        }

        if (line.startsWith(OUTPUTPATH)) {
          moduleOutputPath = reader.readLine();
        }

        if (line.startsWith(TEST_OUTPUTPATH)) {
          moduleTestOutputPath = reader.readLine();
        }

        if (line.startsWith(MODULE_NAME)) {
          moduleName = reader.readLine();
        }

        if (line.startsWith(SRC_FOLDERS)) {
          moduleSourceFoldersPath = reader.readLine();
        }

        line = reader.readLine();
      }

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        reader.close();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        argsFile.delete();
      }
    }


    MyGroovyCompiler groovyCompiler = new MyGroovyCompiler();
    if (srcFiles.isEmpty() && testFiles.isEmpty()) return;

    MyCompilationUnits myCompilationUnits = createCompilationUnits(srcFiles, testFiles, Charset.defaultCharset().toString(), moduleName, moduleClasspath, moduleTestOutputPath, moduleOutputPath, moduleSourceFoldersPath);

    MessageCollector messageCollector = new MessageCollector();
    MyGroovyCompiler.MyExitStatus exitStatus = groovyCompiler.compile(messageCollector, myCompilationUnits);


    MyCompilationUnits.OutputItem[] successfullyCompiled = exitStatus.getSuccessfullyCompiled();
    Set<File> allCompiling = new HashSet<File>();
    allCompiling.addAll(srcFiles);
    allCompiling.addAll(testFiles);

    File[] toRecompilesFiles = successfullyCompiled.length > 0 ? new File[0] : allCompiling.toArray(new File[0]);

    CompilerMessage[] compilerMessages = messageCollector.getAllMessage();

    /*
    * output path
    * source file
    * output root directory
    */

    System.out.println();
    for (MyCompilationUnits.OutputItem compiledOutputItem : successfullyCompiled) {
      System.out.print(COMPILED_START);
      System.out.print(compiledOutputItem.getOutputPath());
      System.out.print(SEPARATOR);
      System.out.print(compiledOutputItem.getSourceFile());
      System.out.print(SEPARATOR);
      System.out.print(compiledOutputItem.getOutputRootDirectory());
      System.out.print(COMPILED_END);
      System.out.println();
    }

    System.out.println();
    for (File toRecompileFile : toRecompilesFiles) {
      System.out.print(TO_RECOMPILE_START);

      try {
        System.out.print(toRecompileFile.getCanonicalPath());
      } catch (IOException e) {
        toRecompileFile.getPath();
      }

      System.out.print(TO_RECOMPILE_END);
      System.out.println();
    }

    for(CompilerMessage message : compilerMessages) {
      System.out.print(MESSAGES_START);

      System.out.print(message.getCathegory());
      System.out.print(SEPARATOR);
      System.out.print(message.getMessage());
      System.out.print(SEPARATOR);
      System.out.print(message.getUrl());
      System.out.print(SEPARATOR);
      System.out.print(message.getLinenum());
      System.out.print(SEPARATOR);
      System.out.print(message.getColomnnum());
      System.out.print(SEPARATOR);

      System.out.print(MESSAGES_END);
      System.out.println();
    }
  }

  private static MyCompilationUnits createCompilationUnits(List<File> srcFilesToCompile, List<File> testFilesToCompile, String characterEncoding, String moduleName, String classpath, String testOutputPath, String ordinaryOutputPath, String sourceFoldersPath) {
    final CompilationUnit sourceUnit = createCompilationUnit(characterEncoding, classpath, ordinaryOutputPath, sourceFoldersPath);
    final CompilationUnit testUnit = createCompilationUnit(characterEncoding, classpath, testOutputPath, sourceFoldersPath);
    MyCompilationUnits myCompilationUnits = myFactory.create(sourceUnit, testUnit);

    for (File fileToCompile : srcFilesToCompile) {
      myCompilationUnits.add(fileToCompile, false);
    }

    for (File fileToCompile : testFilesToCompile) {
      myCompilationUnits.add(fileToCompile, true);
    }


    return myCompilationUnits;
  }

  private static CompilationUnit createCompilationUnit(String characterEncoding, String classpath, String outputPath, String sourceFoldersPath) {
    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
    compilerConfiguration.setSourceEncoding(characterEncoding);
    compilerConfiguration.setOutput(new PrintWriter(System.err));
    compilerConfiguration.setWarningLevel(WarningMessage.PARANOIA);

    compilerConfiguration.setClasspath(classpath);

    compilerConfiguration.setTargetDirectory(outputPath);

    return new CompilationUnit(compilerConfiguration, null, buildClassLoaderFor(compilerConfiguration));
  }

  static GroovyClassLoader buildClassLoaderFor(final CompilerConfiguration compilerConfiguration) {
    return AccessController.doPrivileged(new PrivilegedAction<GroovyClassLoader>() {
      public GroovyClassLoader run() {
        URLClassLoader urlClassLoader = new URLClassLoader(convertClasspathToUrls(compilerConfiguration));
        return new GroovyClassLoader(urlClassLoader, compilerConfiguration);
      }
    });
  }

  private static URL[] convertClasspathToUrls(CompilerConfiguration compilerConfiguration) {
    try {
      return classpathAsUrls(compilerConfiguration);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static URL[] classpathAsUrls(CompilerConfiguration compilerConfiguration) throws MalformedURLException {
    List classpath = compilerConfiguration.getClasspath();
    URL[] classpathUrls = new URL[classpath.size()];
    for (int i = 0; i < classpathUrls.length; i++) {
      String classpathEntry = (String) classpath.get(i);
      classpathUrls[i] = new File(classpathEntry).toURL();
    }
    return classpathUrls;
  }
}
