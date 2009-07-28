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

import com.yourkit.api.Controller;
import com.yourkit.api.ProfilingModes;
import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.tools.javac.JavaStubCompilationUnit;

import java.io.*;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */

public class GroovycRunner {

  public static final String CLASSPATH = "classpath";
  public static final String IS_GRAILS = "is_grails";
  public static final String FOR_STUBS = "for_stubs";
  public static final String ENCODING = "encoding";
  public static final String OUTPUTPATH = "outputpath";
  public static final String TEST_OUTPUTPATH = "test_outputpath";
  public static final String TEST_FILE = "test_file";
  public static final String END = "end";

  public static final String SRC_FILE = "src_file";
  public static final String COMPILED_START = "%%c";

  public static final String COMPILED_END = "/%c";
  public static final String TO_RECOMPILE_START = "%%rc";

  public static final String TO_RECOMPILE_END = "/%rc";
  public static final String MESSAGES_START = "%%m";

  public static final String MESSAGES_END = "/%m";
  public static final String SEPARATOR = "#";

  public static final Controller ourController = initController();
  public static final String PRESENTABLE_MESSAGE = "@#$%@# Presentable:";
  public static final String CLEAR_PRESENTABLE = "$@#$%^ CLEAR_PRESENTABLE";

  private GroovycRunner() {
  }

  private static Controller initController() {
    if (!"true".equals(System.getProperty("profile.groovy.compiler"))) {
      return null;
    }

    try {
      return new Controller();
    }
    catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }


  public static void main(String[] args) {
    if (ourController != null) {
      try {
        ourController.startCPUProfiling(ProfilingModes.CPU_SAMPLING, null);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    try {
      String moduleClasspath = null;
      String moduleOutputPath = null;
      String moduleTestOutputPath = null;
      boolean isGrails = false;
      boolean forStubs = false;
      String encoding = null;

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
      Map class2File = new HashMap();

      BufferedReader reader = null;
      FileInputStream stream;

      try {
        stream = new FileInputStream(argsFile);
        reader = new BufferedReader(new InputStreamReader(stream));

        String line;

        while ((line = reader.readLine()) != null && !line.equals(CLASSPATH)) {
          if (TEST_FILE.equals(line)) {
            testFiles.add(new File(reader.readLine()));
          }
          else if (SRC_FILE.equals(line)) {
            final File file = new File(reader.readLine());
            srcFiles.add(file);
            String s;
            while (!END.equals(s = reader.readLine())) {
              class2File.put(s, file);
            }
          }
        }

        while (line != null) {
          if (line.startsWith(CLASSPATH)) {
            moduleClasspath = reader.readLine();
          }

          if (line.startsWith(IS_GRAILS)) {
            String s = reader.readLine();
            isGrails = "true".equals(s);
          }

          if (line.startsWith(FOR_STUBS)) {
            String s = reader.readLine();
            forStubs = "true".equals(s);
          }

          if (line.startsWith(ENCODING)) {
            encoding = reader.readLine();
          }

          if (line.startsWith(OUTPUTPATH)) {
            moduleOutputPath = reader.readLine();
          }

          if (line.startsWith(TEST_OUTPUTPATH)) {
            moduleTestOutputPath = reader.readLine();
          }

          line = reader.readLine();
        }

      }
      catch (FileNotFoundException e) {
        e.printStackTrace();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      finally {
        try {
          reader.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
        finally {
          argsFile.delete();
        }
      }


      if (srcFiles.isEmpty() && testFiles.isEmpty()) return;

      MessageCollector messageCollector = new MessageCollector();
      final List compiledFiles = new ArrayList();

      System.out.println(PRESENTABLE_MESSAGE + "Groovy compiler: loading sources...");
      createCompilationUnits(srcFiles, class2File, moduleClasspath, moduleOutputPath, isGrails, encoding, forStubs).compile(messageCollector, compiledFiles);

      System.out.println(PRESENTABLE_MESSAGE + "Groovy compiler: loading test sources...");
      createCompilationUnits(testFiles, class2File, moduleClasspath, moduleTestOutputPath, isGrails, encoding, forStubs).compile(messageCollector, compiledFiles);

      System.out.println(CLEAR_PRESENTABLE);

      Set allCompiling = new HashSet();
      allCompiling.addAll(srcFiles);
      allCompiling.addAll(testFiles);

      File[] toRecompilesFiles =
        !compiledFiles.isEmpty() ? new File[0] : (File[])allCompiling.toArray(new File[allCompiling.size()]);

      CompilerMessage[] compilerMessages = messageCollector.getAllMessage();

      /*
      * output path
        * source file
        * output root directory
        */

      System.out.println();
      for (int i = 0; i < compiledFiles.size(); i++) {
        MyCompilationUnits.OutputItem compiledOutputItem = (MyCompilationUnits.OutputItem)compiledFiles.get(i);
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
        }
        catch (IOException e) {
          toRecompileFile.getPath();
        }

        System.out.print(TO_RECOMPILE_END);
        System.out.println();
      }

      int errorCount = 0;
      for (int i = 0; i < compilerMessages.length; i++) {
        CompilerMessage message = compilerMessages[i];

        final String category = message.getCategory();
        final boolean isError = category == MessageCollector.ERROR;
        if (isError) {
          if (errorCount > 100) {
            continue;
          }
          errorCount++;
        }

        System.out.print(MESSAGES_START);

        System.out.print(category);
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
    catch (Throwable e) {
      e.printStackTrace();
    }
    finally {
      if (ourController != null) {
        try {
          ourController.captureSnapshot(ProfilingModes.SNAPSHOT_WITHOUT_HEAP);
          ourController.stopCPUProfiling();
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }
                                  
  private static MyCompilationUnits createCompilationUnits(List srcFilesToCompile, Map class2File, String classpath, 
                                                           String ordinaryOutputPath,
                                                           boolean isGrailsModule,
                                                           final String encoding,
                                                           boolean forStubs) {
    final CompilationUnit unit = createCompilationUnit(class2File, classpath, ordinaryOutputPath, isGrailsModule, encoding, forStubs);
    MyCompilationUnits myCompilationUnits = new MyCompilationUnits(unit, forStubs);

    for (int i = 0; i < srcFilesToCompile.size(); i++) {
      myCompilationUnits.addSource((File) srcFilesToCompile.get(i));
    }

    return myCompilationUnits;
  }

  private static CompilationUnit createCompilationUnit(Map class2File, String classpath, String outputPath, boolean isGrailsModule,
                                                       final String encoding, boolean forStubs) {
    CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
    compilerConfiguration.setOutput(new PrintWriter(System.err));
    compilerConfiguration.setWarningLevel(WarningMessage.PARANOIA);
    if (encoding != null){
      compilerConfiguration.setSourceEncoding(encoding);
    }
    compilerConfiguration.setClasspath(classpath);
    compilerConfiguration.setTargetDirectory(outputPath);

    final GroovyClassLoader classLoader = buildClassLoaderFor(compilerConfiguration);

    final CompilationUnit compilationUnit;
    if (forStubs) {
      compilationUnit = new JavaStubCompilationUnit(compilerConfiguration, classLoader, new File(outputPath)) {
        public void gotoPhase(int phase) throws CompilationFailedException {
          super.gotoPhase(phase);
          if (phase <= Phases.ALL) {
            System.out.println(PRESENTABLE_MESSAGE + "Groovy stub generator: " + getPhaseDescription());
          }
        }
      };
    }
    else {
      compilationUnit = new CompilationUnit(compilerConfiguration, null, classLoader) {

        public void gotoPhase(int phase) throws CompilationFailedException {
          super.gotoPhase(phase);
          if (phase <= Phases.ALL) {
            System.out.println(PRESENTABLE_MESSAGE + "Groovy compiler: " + getPhaseDescription());
          }
        }
      };
    }

    /**
     * Adding here framework-specific Phase operations
     * @see org.codehaus.groovy.control.CompilationUnit#addPhaseOperation(org.codehaus.groovy.control.CompilationUnit.PrimaryClassNodeOperation, int)
     */
    if (isGrailsModule) {
      PhaseOperationUtil.addGrailsAwareInjectionOperation(class2File, compilationUnit, compilerConfiguration);
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
