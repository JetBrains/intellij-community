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
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit;

import java.io.*;
import java.lang.reflect.*;
import java.net.URI;
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
      final CompilerConfiguration config = new CompilerConfiguration();
      config.setClasspath("");
      config.setOutput(new PrintWriter(System.err));
      config.setWarningLevel(WarningMessage.PARANOIA);

      final List<CompilerMessage> compilerMessages = new ArrayList<CompilerMessage>();
      final List<CompilationUnitPatcher> patchers = new ArrayList<CompilationUnitPatcher>();
      final List<File> srcFiles = new ArrayList<File>();
      final Map<String, File> class2File = new HashMap<String, File>();

      final String[] finalOutput = new String[1];
      fillFromArgsFile(argsFile, config, patchers, compilerMessages, srcFiles, class2File, finalOutput);
      if (srcFiles.isEmpty()) return;

      if (forStubs) {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("stubDir", config.getTargetDirectory());
        options.put("keepStubs", Boolean.TRUE);
        config.setJointCompilationOptions(options);

        config.setTargetBytecode(CompilerConfiguration.PRE_JDK5);
      }

      System.out.println(PRESENTABLE_MESSAGE + "Groovyc: loading sources...");
      final AstAwareResourceLoader resourceLoader = new AstAwareResourceLoader(class2File);
      final CompilationUnit unit = createCompilationUnit(forStubs, config, finalOutput[0], buildClassLoaderFor(config, resourceLoader));
      unit.addPhaseOperation(new CompilationUnit.SourceUnitOperation() {
        public void call(SourceUnit source) throws CompilationFailedException {
          File file = new File(source.getName());
          for (ClassNode aClass : source.getAST().getClasses()) {
            resourceLoader.myClass2File.put(aClass.getName(), file);
          }
        }
      }, Phases.CONVERSION);

      addSources(forStubs, srcFiles, unit);
      runPatchers(patchers, compilerMessages, unit, resourceLoader);

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
      for (CompilerMessage message : compilerMessages) {
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

  private static String fillFromArgsFile(File argsFile, CompilerConfiguration compilerConfiguration, List<CompilationUnitPatcher> patchers, List<CompilerMessage> compilerMessages,
                                         List<File> srcFiles, Map<String, File> class2File, String[] finalOutput) {
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
      }

      while (line != null) {
        if (line.equals("class2src")) {
          while (!END.equals(line = reader.readLine())) {
            class2File.put(line, new File(reader.readLine()));
          }
        }
        else if (line.startsWith(PATCHERS)) {
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

  private static void addSources(boolean forStubs, List<File> srcFiles, final CompilationUnit unit) {
    for (final File file : srcFiles) {
      if (forStubs && file.getName().endsWith(".java")) {
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

  private static void runPatchers(List<CompilationUnitPatcher> patchers, List<CompilerMessage> compilerMessages, CompilationUnit unit, final AstAwareResourceLoader loader) {
    if (!patchers.isEmpty()) {
      for (CompilationUnitPatcher patcher : patchers) {
        try {
          patcher.patchCompilationUnit(unit, loader);
        }
        catch (LinkageError e) {
          addExceptionInfo(compilerMessages, e, "Couldn't run " + patcher.getClass().getName());
        }
      }
    }
  }

  private static void reportNotCompiledItems(Collection<File> toRecompile) {
    for (File file : toRecompile) {
      System.out.print(TO_RECOMPILE_START);
      System.out.print(file.getAbsolutePath());
      System.out.print(TO_RECOMPILE_END);
      System.out.println();
    }
  }

  private static void reportCompiledItems(List<GroovyCompilerWrapper.OutputItem> compiledFiles) {
    for (GroovyCompilerWrapper.OutputItem compiledFile : compiledFiles) {
      /*
      * output path
      * source file
      * output root directory
      */
      System.out.print(COMPILED_START);
      System.out.print(compiledFile.getOutputPath());
      System.out.print(SEPARATOR);
      System.out.print(compiledFile.getSourceFile());
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

  private static void addExceptionInfo(List<CompilerMessage> compilerMessages, Throwable e, String message) {
    final StringWriter writer = new StringWriter();
    e.printStackTrace(new PrintWriter(writer));
    compilerMessages.add(new CompilerMessage(CompilerMessage.WARNING, message + ":\n" + writer, "<exception>", -1, -1));
  }

  private static CompilationUnit createCompilationUnit(final boolean forStubs,
                                                       final CompilerConfiguration config,
                                                       final String finalOutput, final GroovyClassLoader classLoader) {

    try {
      if (forStubs) {
        return createStubGenerator(config, classLoader);
      }
    }
    catch (NoClassDefFoundError ignore) { // older groovy distributions just don't have stub generation capability
    }

    final GroovyClassLoader transformLoader = new GroovyClassLoader(classLoader) {
      public Enumeration<URL> getResources(String name) throws IOException {
        if (name.endsWith("org.codehaus.groovy.transform.ASTTransformation")) {
          final Enumeration<URL> resources = super.getResources(name);
          final ArrayList<URL> list = Collections.list(resources);
          for (Iterator iterator = list.iterator(); iterator.hasNext();) {
            final URL url = (URL)iterator.next();
            try {
              final String file = new File(new URI(url.toString())).getCanonicalPath();
              if (file.startsWith(finalOutput) || file.startsWith("/" + finalOutput)) {
                iterator.remove();
              }
            }
            catch (Exception ignored) {
              System.out.println("Invalid URI syntax: " + url.toString());
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
            System.out.println(PRESENTABLE_MESSAGE + "Groovyc: " + getPhaseDescription());
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
            System.out.println(PRESENTABLE_MESSAGE + "Groovyc: " + getPhaseDescription());
          }
        }
      };
    }
    return unit;
  }

  private static CompilationUnit createStubGenerator(final CompilerConfiguration config, final GroovyClassLoader classLoader) {
    JavaAwareCompilationUnit unit = new JavaAwareCompilationUnit(config, classLoader) {
      public void gotoPhase(int phase) throws CompilationFailedException {
        if (phase == Phases.SEMANTIC_ANALYSIS) {
          System.out.println(PRESENTABLE_MESSAGE + "Generating Groovy stubs...");
          // clear javaSources field so that no javac is invoked
          try {
            Field field = JavaAwareCompilationUnit.class.getDeclaredField("javaSources");
            field.setAccessible(true);
            LinkedList javaSources = (LinkedList)field.get(this);
            javaSources.clear();
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }
        else if (phase <= Phases.ALL) {
          System.out.println(PRESENTABLE_MESSAGE + "Groovy stub generator: " + getPhaseDescription());
        }

        super.gotoPhase(phase);
      }

    };
    unit.addSources(new String[]{"SomeClass.java"});
    return unit;
  }

  static GroovyClassLoader buildClassLoaderFor(final CompilerConfiguration compilerConfiguration, final AstAwareResourceLoader resourceLoader) {
    GroovyClassLoader classLoader = AccessController.doPrivileged(new PrivilegedAction<GroovyClassLoader>() {
      public GroovyClassLoader run() {
        return new GroovyClassLoader(getClass().getClassLoader(), compilerConfiguration) {
          public Class loadClass(String name, boolean lookupScriptFiles, boolean preferClassOverScript)
            throws ClassNotFoundException, CompilationFailedException {
            Class aClass;
            try {
              aClass = super.loadClass(name, lookupScriptFiles, preferClassOverScript);
            }
            catch (NoClassDefFoundError e) {
              throw new ClassNotFoundException(name);
            }
            catch (LinkageError e) {
              throw new RuntimeException("Problem loading class " + name, e);
            }

            ensureWellFormed(aClass, new HashSet<Class>());

            return aClass;
          }

          private void ensureWellFormed(Type aClass, Set<Class> visited) throws ClassNotFoundException {
            if (aClass instanceof Class) {
              ensureWellFormed((Class)aClass, visited);
            }
            else if (aClass instanceof ParameterizedType) {
              ensureWellFormed(((ParameterizedType)aClass).getOwnerType(), visited);
              for (Type type : ((ParameterizedType)aClass).getActualTypeArguments()) {
                ensureWellFormed(type, visited);
              }
            }
            else if (aClass instanceof WildcardType) {
              for (Type type : ((WildcardType)aClass).getLowerBounds()) {
                ensureWellFormed(type, visited);
              }
              for (Type type : ((WildcardType)aClass).getUpperBounds()) {
                ensureWellFormed(type, visited);
              }
            }
            else if (aClass instanceof GenericArrayType) {
              ensureWellFormed(((GenericArrayType)aClass).getGenericComponentType(), visited);
            }
          }
          private void ensureWellFormed(Class aClass, Set<Class> visited) throws ClassNotFoundException {
            String name = aClass.getName();
            if (resourceLoader.getSourceFile(name) != null && visited.add(aClass)) {
              try {
                for (Method method : aClass.getDeclaredMethods()) {
                  ensureWellFormed(method.getGenericReturnType(), visited);
                  for (Type type : method.getGenericExceptionTypes()) {
                    ensureWellFormed(type, visited);
                  }
                  for (Type type : method.getGenericParameterTypes()) {
                    ensureWellFormed(type, visited);
                  }
                }

                for (Field field : aClass.getDeclaredFields()) {
                  ensureWellFormed(field.getGenericType(), visited);
                }

                for (Class inner : aClass.getDeclaredClasses()) {
                  ensureWellFormed(inner, visited);
                }

                Type superclass = aClass.getGenericSuperclass();
                if (superclass != null) {
                  ensureWellFormed(aClass, visited);
                }

                for (Type intf : aClass.getGenericInterfaces()) {
                  ensureWellFormed(intf, visited);
                }
              }
              catch (LinkageError e) {
                throw new ClassNotFoundException(name);
              }
              catch (TypeNotPresentException e) {
                throw new ClassNotFoundException(name);
              }
            }
          }
        };
      }
    });
    classLoader.setResourceLoader(resourceLoader);
    return classLoader;
  }

}
