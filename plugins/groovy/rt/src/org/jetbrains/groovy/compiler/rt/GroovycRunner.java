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
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.tools.javac.JavaAwareResolveVisitor;
import org.codehaus.groovy.tools.javac.JavaStubGenerator;

import java.io.*;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 * @noinspection UseOfSystemOutOrSystemErr,CallToPrintStackTrace
 */

public class GroovycRunner {

  public static final String PATCHERS = "patchers";
  public static final String ENCODING = "encoding";
  public static final String OUTPUTPATH = "outputpath";
  public static final String FINAL_OUTPUTPATH = "final_outputpath";
  public static final String END = "end";

  public static final String SRC_FILE = "src_file";
  public static final String COMPILED_START = "%%c";

  public static final String COMPILED_END = "/%c";
  public static final String TO_RECOMPILE_START = "%%rc";

  public static final String TO_RECOMPILE_END = "/%rc";
  public static final String MESSAGES_START = "%%m";

  public static final String MESSAGES_END = "/%m";
  public static final String SEPARATOR = "#%%#%%%#%%%%%%%%%#";

  //public static final Controller ourController = initController();
  public static final String PRESENTABLE_MESSAGE = "@#$%@# Presentable:";
  public static final String CLEAR_PRESENTABLE = "$@#$%^ CLEAR_PRESENTABLE";

  private GroovycRunner() {
  }

  /*
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
  */


  public static void main(String[] args) {
    /*
    if (ourController != null) {
      try {
        ourController.startCPUProfiling(ProfilingModes.CPU_SAMPLING, null);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    */

    if (args.length != 2) {
      System.err.println("There is no arguments for groovy compiler");
      return;
    }

    final boolean forStubs = "stubs".equals(args[0]);
    final File argsFile = new File(args[1]);

    if (!argsFile.exists()) {
      System.err.println("Arguments file for groovy compiler not found");
      return;
    }

    try {
      final CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
      compilerConfiguration.setOutput(new PrintWriter(System.err));
      compilerConfiguration.setWarningLevel(WarningMessage.PARANOIA);

      final List compilerMessages = new ArrayList();
      final List patchers = new ArrayList();
      final List srcFiles = new ArrayList();
      final Map class2File = new HashMap();

      final String[] finalOutput = new String[1];
      fillFromArgsFile(argsFile, compilerConfiguration, patchers, compilerMessages, srcFiles, class2File, finalOutput);
      if (srcFiles.isEmpty()) return;

      System.out.println(PRESENTABLE_MESSAGE + "Groovy compiler: loading sources...");
      final CompilationUnit unit = createCompilationUnit(forStubs, compilerConfiguration, finalOutput[0]);
      addSources(forStubs, srcFiles, unit);
      runPatchers(patchers, compilerMessages, class2File, unit);

      System.out.println(PRESENTABLE_MESSAGE + "Groovyc: compiling...");
      final List compiledFiles = GroovyCompilerWrapper.compile(compilerMessages, forStubs, unit);
      System.out.println(CLEAR_PRESENTABLE);

      System.out.println();
      reportCompiledItems(compiledFiles);

      System.out.println();
      if (compiledFiles.isEmpty()) {
        reportNotCompiledItems(srcFiles);
      }

      int errorCount = 0;
      for (int i = 0; i < compilerMessages.size(); i++) {
        CompilerMessage message = (CompilerMessage)compilerMessages.get(i);

        if (message.getCategory() == CompilerMessage.ERROR) {
          if (errorCount > 100) {
            continue;
          }
          errorCount++;
        }

        printMessage(message);
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
    /*
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
    */
  }

  private static String fillFromArgsFile(File argsFile, CompilerConfiguration compilerConfiguration, List patchers, List compilerMessages,
                                         List srcFiles, Map class2File, String[] finalOutput) {
    String moduleClasspath = null;

    BufferedReader reader = null;
    FileInputStream stream;

    try {
      stream = new FileInputStream(argsFile);
      reader = new BufferedReader(new InputStreamReader(stream));

      String line;

      while ((line = reader.readLine()) != null) {
        if (!SRC_FILE.equals(line)) {
          break;
        }

        final File file = new File(reader.readLine());
        srcFiles.add(file);
        while (!END.equals(line = reader.readLine())) {
          class2File.put(line, file);
        }

      }

      while (line != null) {
        if (line.startsWith(PATCHERS)) {
          String s;
          while (!END.equals(s = reader.readLine())) {
            try {
              final CompilationUnitPatcher patcher = (CompilationUnitPatcher)Class.forName(s).newInstance();
              patchers.add(patcher);
            }
            catch (InstantiationException e) {
              addExceptionInfo(compilerMessages, e, "Couldn't instantiate " + s);
            }
            catch (IllegalAccessException e) {
              addExceptionInfo(compilerMessages, e, "Couldn't instantiate " + s);
            }
            catch (ClassNotFoundException e) {
              addExceptionInfo(compilerMessages, e, "Couldn't instantiate " + s);
            }
          }
        }
        else if (line.startsWith(ENCODING)) {
          compilerConfiguration.setSourceEncoding(reader.readLine());
        }
        else if (line.startsWith(OUTPUTPATH)) {
          compilerConfiguration.setTargetDirectory(reader.readLine());
        }
        else if (line.startsWith(FINAL_OUTPUTPATH)) {
          finalOutput[0] = reader.readLine();
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
    return moduleClasspath;
  }

  private static void addSources(boolean forStubs, List srcFiles, final CompilationUnit unit) {
    for (int i = 0; i < srcFiles.size(); i++) {
      final File file = (File)srcFiles.get(i);
      if (forStubs && file.getName().endsWith(".java")) {
      //  unit.addSources(new File[]{file});
        continue;
      }

      unit.addSource(new SourceUnit(file, unit.getConfiguration(), unit.getClassLoader(), unit.getErrorCollector()) {
        public void parse() throws CompilationFailedException {
          System.out.println(PRESENTABLE_MESSAGE + "Parsing " + file.getName() + "...");
          super.parse();
          System.out.println(CLEAR_PRESENTABLE);
        }
      });
    }
  }

  private static void runPatchers(List patchers, List compilerMessages, Map class2File, CompilationUnit unit) {
    if (!patchers.isEmpty()) {
      final PsiAwareResourceLoader loader = new PsiAwareResourceLoader(class2File);
      for (int i = 0; i < patchers.size(); i++) {
        final CompilationUnitPatcher patcher = (CompilationUnitPatcher)patchers.get(i);
        try {
          patcher.patchCompilationUnit(unit, loader);
        }
        catch (LinkageError e) {
          addExceptionInfo(compilerMessages, e, "Couldn't run " + patcher.getClass().getName());
        }
      }
    }
  }

  private static void reportNotCompiledItems(Collection toRecompile) {
    for (Iterator iterator = toRecompile.iterator(); iterator.hasNext();) {
      File file = (File)iterator.next();
      System.out.print(TO_RECOMPILE_START);
      System.out.print(file.getAbsolutePath());
      System.out.print(TO_RECOMPILE_END);
      System.out.println();
    }
  }

  private static void reportCompiledItems(List compiledFiles) {
    for (int i = 0; i < compiledFiles.size(); i++) {
      /*
      * output path
      * source file
      * output root directory
      */
      GroovyCompilerWrapper.OutputItem compiledOutputItem = (GroovyCompilerWrapper.OutputItem)compiledFiles.get(i);
      System.out.print(COMPILED_START);
      System.out.print(compiledOutputItem.getOutputPath());
      System.out.print(SEPARATOR);
      System.out.print(compiledOutputItem.getSourceFile());
      System.out.print(SEPARATOR);
      System.out.print(compiledOutputItem.getOutputRootDirectory());
      System.out.print(COMPILED_END);
      System.out.println();
    }
  }

  private static void printMessage(CompilerMessage message) {
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

  private static void addExceptionInfo(List compilerMessages, Throwable e, String message) {
    final StringWriter writer = new StringWriter();
    e.printStackTrace(new PrintWriter(writer));
    compilerMessages.add(new CompilerMessage(CompilerMessage.WARNING, message + ":\n" + writer, "<exception>", -1, -1));
  }

  private static CompilationUnit createCompilationUnit(final boolean forStubs, final CompilerConfiguration config, final String finalOutput) {
    config.setClasspath("");

    final GroovyClassLoader classLoader = buildClassLoaderFor(config);

    final GroovyClassLoader transformLoader = new GroovyClassLoader(classLoader) {
      public Enumeration getResources(String name) throws IOException {
        if (name.endsWith("org.codehaus.groovy.transform.ASTTransformation")) {
          final Enumeration resources = super.getResources(name);
          final ArrayList list = Collections.list(resources);
          for (Iterator iterator = list.iterator(); iterator.hasNext();) {
            String file = ((URL)iterator.next()).getFile();
            System.out.println("Enumerated:" + file);
            if (file.startsWith(finalOutput) || file.startsWith("/" + finalOutput)) {
              iterator.remove();
            }
          }
          return Collections.enumeration(list);
        }
        return super.getResources(name);
      }
    };
    CompilationUnit unit;
    try {
      unit = new CompilationUnit(config, null, classLoader, transformLoader) {

        public void gotoPhase(int phase) throws CompilationFailedException {
          super.gotoPhase(phase);
          if (phase <= Phases.ALL) {
            System.out.println(PRESENTABLE_MESSAGE + (forStubs ? "Groovy stub generator: " : "Groovy compiler: ") + getPhaseDescription());
          }
        }
      };
    }
    catch (NoSuchMethodError e) {
      //groovy 1.5.x
      unit = new CompilationUnit(config, null, classLoader) {

        public void gotoPhase(int phase) throws CompilationFailedException {
          super.gotoPhase(phase);
          if (phase <= Phases.ALL) {
            System.out.println(PRESENTABLE_MESSAGE + (forStubs ? "Groovy stub generator: " : "Groovy compiler: ") + getPhaseDescription());
          }
        }
      };
    }
    if (forStubs) {
      try {
        addStubGeneration(config, unit);
      }
      catch (LinkageError e) {
        //older groovy distributions, just don't generate stubs
      }
    }
    return unit;
  }

  private static void addStubGeneration(CompilerConfiguration config, final CompilationUnit unit) {
    //todo reuse JavaStubCompilationUnit in groovy 1.7
    boolean useJava5 = config.getTargetBytecode().equals(CompilerConfiguration.POST_JDK5);
    final JavaStubGenerator stubGenerator = new JavaStubGenerator(config.getTargetDirectory(), false, useJava5);

    //but JavaStubCompilationUnit doesn't have this...
    unit.addPhaseOperation(new CompilationUnit.PrimaryClassNodeOperation() {
      public void call(SourceUnit source, GeneratorContext context, ClassNode node) throws CompilationFailedException {
        new JavaAwareResolveVisitor(unit).startResolving(node, source);
      }
    }, Phases.CONVERSION);

    unit.addPhaseOperation(new CompilationUnit.PrimaryClassNodeOperation() {
      public void call(final SourceUnit source, final GeneratorContext context, final ClassNode node) throws CompilationFailedException {
        final String name = node.getNameWithoutPackage();
        if ("package-info".equals(name)) {
          return;
        }

        System.out.println(PRESENTABLE_MESSAGE + "Generating stub for " + name);
        try {
          stubGenerator.generateClass(node);
        }
        catch (FileNotFoundException e) {
          source.addException(e);
        }
      }
    },Phases.CONVERSION);
  }

  static GroovyClassLoader buildClassLoaderFor(final CompilerConfiguration compilerConfiguration) {
    return (GroovyClassLoader) AccessController.doPrivileged(new PrivilegedAction() {
      public Object run() {
        return new GroovyClassLoader(getClass().getClassLoader(), compilerConfiguration) {
          public Class loadClass(String name, boolean lookupScriptFiles, boolean preferClassOverScript)
            throws ClassNotFoundException, CompilationFailedException {
            try {
              return super.loadClass(name, lookupScriptFiles, preferClassOverScript);
            }
            catch (NoClassDefFoundError e) {
              final String ncdfe = e.getMessage();
              
              throw new RuntimeException("Groovyc error: " + ncdfe + " class not found while resolving class " + name + "; try compiling " + ncdfe + " explicitly", e);
            }
            catch (LinkageError e) {
              throw new RuntimeException("Problem loading class " + name, e);
            }
          }
        };
      }
    });
  }

}
