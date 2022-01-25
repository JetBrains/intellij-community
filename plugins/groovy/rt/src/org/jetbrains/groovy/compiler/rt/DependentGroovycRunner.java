// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.groovy.compiler.rt;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit;
import org.codehaus.groovy.tools.javac.JavaCompiler;
import org.codehaus.groovy.tools.javac.JavaCompilerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author peter
 */
public final class DependentGroovycRunner {
  public static final String TEMP_RESOURCE_SUFFIX = "___" + new Random().nextInt() + "_neverHappen";
  public static final String[] RESOURCES_TO_MASK = {
    "META-INF/services/org.codehaus.groovy.transform.ASTTransformation",
    "META-INF/services/org.codehaus.groovy.runtime.ExtensionModule"
  };
  private static final String STUB_DIR = "stubDir";

  @SuppressWarnings("unused")
  public static boolean runGroovyc(boolean forStubs,
                                   String argsPath,
                                   @Nullable String configScript,
                                   @Nullable String targetBytecode,
                                   @Nullable Queue<? super Object> mailbox,
                                   @NotNull PrintStream out,
                                   @NotNull PrintStream err) {
    File argsFile = new File(argsPath);
    CompilerConfiguration config = createCompilerConfiguration(targetBytecode);
    config.setClasspath("");
    //noinspection deprecation,ImplicitDefaultCharsetUsage
    config.setOutput(new PrintWriter(err));
    config.setWarningLevel(WarningMessage.PARANOIA);

    final List<GroovyCompilerMessage> compilerMessages = new ArrayList<GroovyCompilerMessage>();
    final List<CompilationUnitPatcher> patchers = new ArrayList<CompilationUnitPatcher>();
    final List<File> srcFiles = new ArrayList<File>();
    final Map<String, File> class2File = new HashMap<String, File>();

    final String[] finalOutputRef = new String[1];
    fillFromArgsFile(argsFile, config, patchers, compilerMessages, srcFiles, class2File, finalOutputRef, err);
    if (srcFiles.isEmpty()) return true;

    String[] finalOutputs = finalOutputRef[0].split(File.pathSeparator);

    if (forStubs) {
      Map<String, Object> options = new HashMap<String, Object>();
      options.put(STUB_DIR, config.getTargetDirectory());
      options.put("keepStubs", Boolean.TRUE);
      config.setJointCompilationOptions(options);
      if (mailbox != null) {
        config.setTargetDirectory(finalOutputs[0]);
      }
    }

    try {
      boolean asm = !"false".equals(System.getProperty(GroovyRtConstants.GROOVYC_ASM_RESOLVING_ONLY));
      config.getOptimizationOptions().put("asmResolving", asm);
      config.getOptimizationOptions().put("classLoaderResolving", !asm);
    }
    catch (NoSuchMethodError ignored) { // old groovyc's don't have optimization options
    }

    if (configScript != null && configScript.length() > 0) {
      try {
        applyConfigurationScript(new File(configScript), config, err);
      }
      catch (LinkageError ignored) {
      }
    }

    out.println(GroovyRtConstants.PRESENTABLE_MESSAGE + "Groovyc: loading sources...");
    renameResources(finalOutputs, "", TEMP_RESOURCE_SUFFIX);

    final List<OutputItem> compiledFiles;
    try {
      final AstAwareResourceLoader resourceLoader = new AstAwareResourceLoader(class2File);
      final GroovyCompilerWrapper wrapper = new GroovyCompilerWrapper(compilerMessages, forStubs);
      final CompilationUnit unit = createCompilationUnit(
        forStubs, config, buildClassLoaderFor(config, resourceLoader), mailbox, wrapper, out, err
      );
      unit.addPhaseOperation(new CompilationUnit.SourceUnitOperation() {
        @Override
        public void call(SourceUnit source) throws CompilationFailedException {
          File file = new File(source.getName());
          for (ClassNode aClass : source.getAST().getClasses()) {
            resourceLoader.myClass2File.put(aClass.getName(), file);
          }
        }

        @SuppressWarnings("RedundantMethodOverride")
        public void doPhaseOperation(CompilationUnit unit) throws CompilationFailedException {
          super.doPhaseOperation(unit);
        }
      }, Phases.CONVERSION);

      addSources(forStubs, srcFiles, unit);
      runPatchers(patchers, compilerMessages, unit, resourceLoader, srcFiles);

      out.println(GroovyRtConstants.PRESENTABLE_MESSAGE + "Groovyc: compiling...");
      compiledFiles = wrapper.compile(unit, forStubs && mailbox == null ? Phases.CONVERSION : Phases.ALL);
    }
    finally {
      renameResources(finalOutputs, TEMP_RESOURCE_SUFFIX, "");
      out.println(GroovyRtConstants.CLEAR_PRESENTABLE);
    }

    out.println();
    reportCompiledItems(out, compiledFiles);

    int errorCount = 0;
    for (GroovyCompilerMessage message : compilerMessages) {
      if (message.getCategory() == GroovyCompilerMessageCategories.ERROR) {
        if (errorCount > 100) {
          continue;
        }
        errorCount++;
      }

      printMessage(out, message);
    }
    return false;
  }

  private static CompilerConfiguration createCompilerConfiguration(@Nullable String targetBytecode) {
    CompilerConfiguration config = new CompilerConfiguration();
    if (targetBytecode != null) {
      config.setTargetBytecode(targetBytecode);
    }

    if (config.getTargetBytecode() == null) {
      // unsupported value (e.g. "1.6" with older Groovyc versions which know only 1.5)

      // clear env because CompilerConfiguration constructor just sets the target bytecode to null on encountering invalid value in the env
      System.clearProperty(GroovyRtConstants.GROOVY_TARGET_BYTECODE);

      // now recreate conf taking the default from VM version
      config = new CompilerConfiguration();

      if (config.getTargetBytecode() == null) {
        throw new AssertionError("Cannot determine bytecode target");
      }
    }
    return config;
  }

  // adapted from https://github.com/gradle/gradle/blob/c4fdfb57d336b1a0f1b27354c758c61c0a586942/subprojects/language-groovy/src/main/java/org/gradle/api/internal/tasks/compile/ApiGroovyCompiler.java
  private static void applyConfigurationScript(File configScript, CompilerConfiguration configuration, PrintStream err) {
    Binding binding = new Binding();
    binding.setVariable("configuration", configuration);

    CompilerConfiguration configuratorConfig = new CompilerConfiguration();
    ImportCustomizer customizer = new ImportCustomizer();
    customizer.addStaticStars("org.codehaus.groovy.control.customizers.builder.CompilerCustomizationBuilder");
    configuratorConfig.addCompilationCustomizers(customizer);

    try {
      new GroovyShell(binding, configuratorConfig).evaluate(configScript);
    }
    catch (Exception e) {
      e.printStackTrace(err);
    }
  }

  private static void renameResources(String[] finalOutputs, String removeSuffix, String addSuffix) {
    for (String output : finalOutputs) {
      for (String res : RESOURCES_TO_MASK) {
        File file = new File(output, res + removeSuffix);
        if (file.exists()) {
          //noinspection ResultOfMethodCallIgnored
          file.renameTo(new File(output, res + addSuffix));
        }
      }
    }
  }

  private static void fillFromArgsFile(File argsFile,
                                       CompilerConfiguration compilerConfiguration,
                                       List<? super CompilationUnitPatcher> patchers,
                                       List<? super GroovyCompilerMessage> compilerMessages,
                                       List<? super File> srcFiles,
                                       Map<String, File> class2File,
                                       String[] finalOutputs,
                                       PrintStream err) {
    BufferedReader reader = null;
    FileInputStream stream;

    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      stream = new FileInputStream(argsFile);
      //noinspection IOResourceOpenedButNotSafelyClosed
      reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));

      reader.readLine(); // skip classpath

      String line;
      while ((line = reader.readLine()) != null) {
        if (!GroovyRtConstants.SRC_FILE.equals(line)) {
          break;
        }

        final File file = new File(reader.readLine());
        srcFiles.add(file);
      }

      while (line != null) {
        if (line.equals("class2src")) {
          while (!GroovyRtConstants.END.equals(line = reader.readLine())) {
            class2File.put(line, new File(reader.readLine()));
          }
        }
        else if (line.startsWith(GroovyRtConstants.PATCHERS)) {
          final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
          String s;
          while (!GroovyRtConstants.END.equals(s = reader.readLine())) {
            try {
              final Class<?> patcherClass = classLoader.loadClass(s);
              final CompilationUnitPatcher patcher = (CompilationUnitPatcher)patcherClass.newInstance();
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
        else if (line.startsWith(GroovyRtConstants.ENCODING)) {
          compilerConfiguration.setSourceEncoding(reader.readLine());
        }
        else if (line.startsWith(GroovyRtConstants.OUTPUTPATH)) {
          compilerConfiguration.setTargetDirectory(reader.readLine());
        }
        else if (line.startsWith(GroovyRtConstants.FINAL_OUTPUTPATH)) {
          finalOutputs[0] = reader.readLine();
        }

        line = reader.readLine();
      }
    }
    catch (FileNotFoundException e) {
      e.printStackTrace(err);
    }
    catch (IOException e) {
      e.printStackTrace(err);
    }
    finally {
      try {
        reader.close();
      }
      catch (IOException e) {
        e.printStackTrace(err);
      }
      finally {
        //noinspection ResultOfMethodCallIgnored
        argsFile.delete();
      }
    }
  }

  private static void addSources(boolean forStubs, List<? extends File> srcFiles, final CompilationUnit unit) {
    for (final File file : srcFiles) {
      if (forStubs && file.getName().endsWith(".java")) {
        continue;
      }

      unit.addSource(new SourceUnit(file, unit.getConfiguration(), unit.getClassLoader(), unit.getErrorCollector()));
    }
  }

  private static void runPatchers(List<? extends CompilationUnitPatcher> patchers,
                                  List<? super GroovyCompilerMessage> compilerMessages,
                                  CompilationUnit unit,
                                  final AstAwareResourceLoader loader,
                                  List<File> srcFiles) {
    if (!patchers.isEmpty()) {
      for (CompilationUnitPatcher patcher : patchers) {
        try {
          patcher.patchCompilationUnit(unit, loader, srcFiles.toArray(new File[0]));
        }
        catch (LinkageError e) {
          addExceptionInfo(compilerMessages, e, "Couldn't run " + patcher.getClass().getName());
        }
      }
    }
  }

  private static void reportCompiledItems(@NotNull PrintStream out, @NotNull List<? extends OutputItem> compiledFiles) {
    for (OutputItem compiledFile : compiledFiles) {
      /*
       * output path
       * source file
       * output root directory
       */
      out.print(GroovyRtConstants.COMPILED_START);
      out.print(compiledFile.outputPath);
      out.print(GroovyRtConstants.SEPARATOR);
      out.print(compiledFile.sourcePath);
      out.print(GroovyRtConstants.COMPILED_END);
      out.println();
    }
  }

  private static void printMessage(@NotNull PrintStream out, @NotNull GroovyCompilerMessage message) {
    out.print(GroovyRtConstants.MESSAGES_START);
    out.print(message.getCategory());
    out.print(GroovyRtConstants.SEPARATOR);
    out.print(message.getMessage());
    out.print(GroovyRtConstants.SEPARATOR);
    out.print(message.getUrl());
    out.print(GroovyRtConstants.SEPARATOR);
    out.print(message.getLineNum());
    out.print(GroovyRtConstants.SEPARATOR);
    out.print(message.getColumnNum());
    out.print(GroovyRtConstants.SEPARATOR);
    out.print(GroovyRtConstants.MESSAGES_END);
    out.println();
  }

  private static void addExceptionInfo(List<? super GroovyCompilerMessage> compilerMessages, Throwable e, String message) {
    final StringWriter writer = new StringWriter();
    e.printStackTrace(new PrintWriter(writer));
    compilerMessages.add(new GroovyCompilerMessage(
      GroovyCompilerMessageCategories.WARNING, message + ":\n" + writer,
      "<exception>", -1, -1
    ));
  }

  private static CompilationUnit createCompilationUnit(final boolean forStubs,
                                                       final CompilerConfiguration config,
                                                       final GroovyClassLoader classLoader,
                                                       Queue<? super Object> mailbox,
                                                       GroovyCompilerWrapper wrapper,
                                                       final PrintStream out,
                                                       PrintStream err) {

    final GroovyClassLoader transformLoader = new GroovyClassLoader(classLoader);

    try {
      if (forStubs) {
        return createStubGenerator(config, classLoader, transformLoader, mailbox, wrapper, out, err);
      }
    }
    catch (NoClassDefFoundError ignore) { // older groovy distributions just don't have stub generation capability
    }

    CompilationUnit unit;
    try {
      unit = new CompilationUnit(config, null, classLoader, transformLoader) {

        @Override
        public void gotoPhase(int phase) throws CompilationFailedException {
          super.gotoPhase(phase);
          if (phase <= Phases.ALL) {
            out.println(GroovyRtConstants.PRESENTABLE_MESSAGE + "Groovyc: " + getPhaseDescription());
          }
        }
      };
    }
    catch (NoSuchMethodError e) {
      //groovy 1.5.x
      unit = new CompilationUnit(config, null, classLoader) {

        @Override
        public void gotoPhase(int phase) throws CompilationFailedException {
          super.gotoPhase(phase);
          if (phase <= Phases.ALL) {
            out.println(GroovyRtConstants.PRESENTABLE_MESSAGE + "Groovyc: " + getPhaseDescription());
          }
        }
      };
    }
    return unit;
  }

  private static CompilationUnit createStubGenerator(final CompilerConfiguration config,
                                                     final GroovyClassLoader classLoader,
                                                     final GroovyClassLoader transformLoader,
                                                     final Queue<? super Object> mailbox,
                                                     final GroovyCompilerWrapper wrapper,
                                                     final PrintStream out,
                                                     final PrintStream err) {
    final JavaAwareCompilationUnit unit = new JavaAwareCompilationUnit(config, classLoader) {
      private boolean annoRemovedAdded;

      @Override
      public GroovyClassLoader getTransformLoader() {
        return transformLoader;
      }

      @Override
      public void addPhaseOperation(PrimaryClassNodeOperation op, int phase) {
        if (!annoRemovedAdded &&
            mailbox == null &&
            phase == Phases.CONVERSION &&
            "true".equals(System.getProperty(GroovyRtConstants.GROOVYC_LEGACY_REMOVE_ANNOTATIONS)) &&
            op.getClass().getName().startsWith("org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit$")) {
          annoRemovedAdded = true;
          super.addPhaseOperation(new PrimaryClassNodeOperation() {
            @Override
            public void call(final SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
              final ClassCodeVisitorSupport annoRemover = new ClassCodeVisitorSupport() {
                @Override
                protected SourceUnit getSourceUnit() {
                  return source;
                }

                @Override
                public void visitClass(ClassNode node) {
                  if (node.isEnum()) {
                    node.setModifiers(node.getModifiers() & ~Opcodes.ACC_FINAL);
                  }
                  super.visitClass(node);
                }

                @Override
                public void visitField(FieldNode fieldNode) {
                  Expression valueExpr = fieldNode.getInitialValueExpression();
                  if (valueExpr instanceof ConstantExpression && ClassHelper.STRING_TYPE.equals(valueExpr.getType())) {
                    fieldNode.setInitialValueExpression(new MethodCallExpression(valueExpr, "toString", new ListExpression()));
                  }
                  super.visitField(fieldNode);
                }

                @Override
                public void visitAnnotations(AnnotatedNode node) {
                  List<AnnotationNode> annotations = node.getAnnotations();
                  if (!annotations.isEmpty()) {
                    annotations.clear();
                  }
                  super.visitAnnotations(node);
                }
              };
              try {
                annoRemover.visitClass(classNode);
              }
              catch (LinkageError ignored) {
              }
            }

            @SuppressWarnings("RedundantMethodOverride")
            public void doPhaseOperation(CompilationUnit unit) throws CompilationFailedException {
              super.doPhaseOperation(unit);
            }
          }, phase);
        }

        super.addPhaseOperation(op, phase);
      }

      @Override
      public void gotoPhase(int phase) throws CompilationFailedException {
        if (phase < Phases.SEMANTIC_ANALYSIS) {
          out.println(GroovyRtConstants.PRESENTABLE_MESSAGE + "Groovy stub generator: " + getPhaseDescription());
        }
        else if (phase <= Phases.ALL) {
          out.println(GroovyRtConstants.PRESENTABLE_MESSAGE + "Groovyc: " + getPhaseDescription());
        }

        super.gotoPhase(phase);
      }
    };
    unit.setCompilerFactory(new JavaCompilerFactory() {
      public JavaCompiler createCompiler(final CompilerConfiguration config) {
        return new JavaCompiler() {
          public void compile(List<String> files, CompilationUnit cu) {
            if (mailbox != null) {
              reportCompiledItems(
                out,
                GroovyCompilerWrapper.getStubOutputItems(unit, (File)config.getJointCompilationOptions().get(STUB_DIR))
              );
              out.flush();
              err.flush();

              pauseAndWaitForJavac(mailbox);
              wrapper.onContinuation();
            }
          }
        };
      }
    });
    unit.addSources(new String[]{"SomeClass.java"});
    return unit;
  }

  private static void pauseAndWaitForJavac(Queue<? super Object> mailbox) {
    LinkedBlockingQueue<String> fromJps = new LinkedBlockingQueue<String>();
    mailbox.offer(fromJps); // signal that stubs are generated
    while (true) {
      try {
        Object response = fromJps.poll(1, TimeUnit.MINUTES);
        if (GroovyRtConstants.BUILD_ABORTED.equals(response)) {
          throw new RuntimeException(GroovyRtConstants.BUILD_ABORTED);
        }
        else if (GroovyRtConstants.JAVAC_COMPLETED.equals(response)) {
          return; // stop waiting and continue compiling
        }
        else if (response != null) {
          throw new RuntimeException("Unknown response: " + response);
        }
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  static GroovyClassLoader buildClassLoaderFor(final CompilerConfiguration compilerConfiguration,
                                               final AstAwareResourceLoader resourceLoader) {
    final ClassDependencyLoader checkWellFormed = new ClassDependencyLoader() {
      @Override
      protected void loadClassDependencies(Class aClass) throws ClassNotFoundException {
        if (resourceLoader.getSourceFile(aClass.getName()) == null) return;
        super.loadClassDependencies(aClass);
      }
    };

    GroovyClassLoader classLoader = AccessController.doPrivileged(new PrivilegedAction<GroovyClassLoader>() {
      public GroovyClassLoader run() {
        return new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), compilerConfiguration) {
          @Override
          public Class<?> loadClass(String name, boolean lookupScriptFiles, boolean preferClassOverScript)
            throws ClassNotFoundException, CompilationFailedException {
            Class<?> aClass;
            try {
              aClass = super.loadClass(name, lookupScriptFiles, preferClassOverScript);
            }
            catch (NoClassDefFoundError e) {
              throw new ClassNotFoundException(name, e);
            }
            catch (LinkageError e) {
              throw new RuntimeException("Problem loading class " + name, e);
            }
            return checkWellFormed.loadDependencies(aClass);
          }
        };
      }
    });
    classLoader.setResourceLoader(resourceLoader);
    return classLoader;
  }
}
