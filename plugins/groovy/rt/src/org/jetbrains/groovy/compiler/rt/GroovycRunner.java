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
import java.net.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */

public class GroovycRunner {
  static CompilationUnitsFactory myFactory = new CompilationUnitsFactory();

  public static final String CLASSPATH = "classpath";
  public static final String IS_GRAILS = "is_grails";
  public static final String OUTPUTPATH = "outputpath";
  public static final String TEST_OUTPUTPATH = "test_outputpath";
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
    boolean isGrails = false;

    if (args.length != 1) {
      System.err.println("There is no arguments for groovy compiler");
      return;
    }

    File argsFile = new File(args[0]);

    if (!argsFile.exists()) {
      System.err.println("Arguments file for groovy compiler not found");
      return;
    }

    List srcFiles = new ArrayList();
    List testFiles = new ArrayList();

    BufferedReader reader = null;
    FileInputStream stream;

    try {
      stream = new FileInputStream(argsFile);
      reader = new BufferedReader(new InputStreamReader(stream));

      String line;

      while ((line = reader.readLine()) != null && !line.equals(CLASSPATH)) {
        if (TEST_FILE.equals(line)) testFiles.add(new File(reader.readLine()));
        else if (SRC_FILE.equals(line)) srcFiles.add(new File(reader.readLine()));
      }

      while (line != null) {
        if (line.startsWith(CLASSPATH)) {
          moduleClasspath = reader.readLine();
        }

        if (line.startsWith(IS_GRAILS)) {
          String s = reader.readLine();
          isGrails = "true".equals(s);
        }

        if (line.startsWith(OUTPUTPATH)) {
          moduleOutputPath = reader.readLine();
        }

        if (line.startsWith(TEST_OUTPUTPATH)) {
          moduleTestOutputPath = reader.readLine();
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

    MyCompilationUnits myCompilationUnits = createCompilationUnits(srcFiles, testFiles, moduleClasspath, moduleTestOutputPath, moduleOutputPath, isGrails);

    MessageCollector messageCollector = new MessageCollector();
    MyGroovyCompiler.MyExitStatus exitStatus = groovyCompiler.compile(messageCollector, myCompilationUnits);


    MyCompilationUnits.OutputItem[] successfullyCompiled = exitStatus.getSuccessfullyCompiled();
    Set allCompiling = new HashSet();
    allCompiling.addAll(srcFiles);
    allCompiling.addAll(testFiles);

    File[] toRecompilesFiles = successfullyCompiled.length > 0 ? new File[0] : (File[])allCompiling.toArray(new File[allCompiling.size()]);

    CompilerMessage[] compilerMessages = messageCollector.getAllMessage();

    /*
    * output path
    * source file
    * output root directory
    */

    System.out.println();
    for (int i = 0; i < successfullyCompiled.length; i++) {
      MyCompilationUnits.OutputItem compiledOutputItem = successfullyCompiled[i];
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
    for (int i = 0; i < toRecompilesFiles.length; i++) {
      File toRecompileFile = toRecompilesFiles[i];
      System.out.print(TO_RECOMPILE_START);

      try {
        System.out.print(toRecompileFile.getCanonicalPath());
      } catch (IOException e) {
        toRecompileFile.getPath();
      }

      System.out.print(TO_RECOMPILE_END);
      System.out.println();
    }

    for (int i = 0; i < compilerMessages.length; i++) {
      CompilerMessage message = compilerMessages[i];
      System.out.print(MESSAGES_START);

      System.out.print(message.getCategory());
      System.out.print(SEPARATOR);
      System.out.print(message.getMessage());
      System.out.print(SEPARATOR);
      System.out.print(message.getUrl());
      System.out.print(SEPARATOR);
      System.out.print(message.getLineNum());
      System.out.print(SEPARATOR);
      System.out.print(message.getColumnNum());
      System.out.print(SEPARATOR);

      System.out.print(MESSAGES_END);
      System.out.println();
    }
  }

  private static MyCompilationUnits createCompilationUnits(List srcFilesToCompile, List testFilesToCompile, String classpath, String testOutputPath, String ordinaryOutputPath, boolean isGrailsModule) {
    final CompilationUnit sourceUnit = createCompilationUnit(srcFilesToCompile, classpath, ordinaryOutputPath, isGrailsModule);
    final CompilationUnit testUnit = createCompilationUnit(testFilesToCompile, classpath, testOutputPath, isGrailsModule);
    MyCompilationUnits myCompilationUnits = myFactory.create(sourceUnit, testUnit);

    for (int i = 0; i < srcFilesToCompile.size(); i++) {
      File fileToCompile = (File) srcFilesToCompile.get(i);
      myCompilationUnits.add(fileToCompile, false);
    }

    for (int i = 0; i < testFilesToCompile.size(); i++) {
      File fileToCompile = (File) testFilesToCompile.get(i);
      myCompilationUnits.add(fileToCompile, true);
    }


    return myCompilationUnits;
  }

  private static CompilationUnit createCompilationUnit(List srcFiles, String classpath, String outputPath, boolean isGrailsModule) {
    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
    compilerConfiguration.setOutput(new PrintWriter(System.err));
    compilerConfiguration.setWarningLevel(WarningMessage.PARANOIA);
    compilerConfiguration.setClasspath(classpath);
    compilerConfiguration.setTargetDirectory(outputPath);

    CompilationUnit compilationUnit = new CompilationUnit(compilerConfiguration, null, buildClassLoaderFor(compilerConfiguration));

    /**
     * Adding here framework-specific Phase operations
     * @see org.codehaus.groovy.control.CompilationUnit#addPhaseOperation(org.codehaus.groovy.control.CompilationUnit.PrimaryClassNodeOperation, int)
     */
    if (isGrailsModule) {
      PhaseOperationUtil.addGrailsAwareInjectionOperation(srcFiles, compilationUnit, compilerConfiguration);
    }
    return compilationUnit;
  }

  static GroovyClassLoader buildClassLoaderFor(final CompilerConfiguration compilerConfiguration) {
    return (GroovyClassLoader) AccessController.doPrivileged(new PrivilegedAction() {
      public Object run() {
        URLClassLoader urlClassLoader = new URLClassLoader(GroovyCompilerUtil.convertClasspathToUrls(compilerConfiguration));
        return new GroovyClassLoader(urlClassLoader, compilerConfiguration);
      }
    });
  }

}
