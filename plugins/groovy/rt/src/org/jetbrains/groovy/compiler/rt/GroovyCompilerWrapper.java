// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.groovy.compiler.rt;

import groovy.lang.GroovyRuntimeException;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.control.messages.*;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.tools.GroovyClass;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;


public class GroovyCompilerWrapper {
  private static final String LINKAGE_ERROR =
    "A groovyc error occurred while trying to load one of the classes in project dependencies, please ensure it's present. " +
    "See the message and the stack trace below for reference\n\n";
  private static final String INCOMPATIBLE_CLASS_CHANGE_ERROR =
    "A groovyc error occurred while trying to load one of the classes in project dependencies. " +
    "Please ensure its version is compatible with other jars (including Groovy ones) in the dependencies. " +
    "See the message and the stack trace below for reference\n\n";
  private final List<? super GroovyCompilerMessage> collector;
  private boolean forStubs;

  public GroovyCompilerWrapper(List<? super GroovyCompilerMessage> collector, boolean forStubs) {
    this.collector = collector;
    this.forStubs = forStubs;
  }

  public void onContinuation() {
    this.forStubs = false;
  }

  public List<OutputItem> compile(final CompilationUnit unit, int throughPhase) {
    try {
      unit.compile(throughPhase);
      return getCompiledFiles(unit);
    }
    catch (CompilationFailedException e) {
      processCompilationException(e);
    }
    catch (IOException | GroovyBugError e) {
      processException(e, "");
    }
    catch (NoClassDefFoundError e) {
      final String className = e.getMessage();
      if (className.contains("org/apache/ivy/")) {
        addMessageWithoutLocation("Cannot @Grab without Ivy, please add it to your module dependencies (NoClassDefFoundError: " + className + ")", true);
      } else {
        throw e;
      }
    }
    catch (TypeNotPresentException e) {
      processException(e, LINKAGE_ERROR);
    }
    catch (IncompatibleClassChangeError e) {
      processException(e, INCOMPATIBLE_CLASS_CHANGE_ERROR);
    }
    catch (LinkageError e) {
      if (e.getCause() instanceof GroovyRuntimeException) {
        processException(e, getExceptionMessage((GroovyRuntimeException)e.getCause()));
      } else {
        processException(e, LINKAGE_ERROR);
      }
    }
    finally {
      addWarnings(unit.getErrorCollector());
    }
    return Collections.emptyList();
  }

  private List<OutputItem> getCompiledFiles(CompilationUnit compilationUnit) throws IOException {
    File targetDirectory = compilationUnit.getConfiguration().getTargetDirectory();

    if (forStubs) {
      return getStubOutputItems(compilationUnit, targetDirectory);
    }

    final SortedSet<String> allClasses = new TreeSet<>();
    //noinspection unchecked
    List<GroovyClass> listOfClasses = compilationUnit.getClasses();
    for (GroovyClass listOfClass : listOfClasses) {
      allClasses.add(listOfClass.getName());
    }

    List<OutputItem> compiledFiles = new ArrayList<>();
    for (Iterator iterator = compilationUnit.iterator(); iterator.hasNext();) {
      SourceUnit sourceUnit = (SourceUnit) iterator.next();
      String fileName = sourceUnit.getName();
      //for debug purposes
      //System.out.println("source: " + fileName);
      //System.out.print("classes:");
      final ModuleNode ast = sourceUnit.getAST();
      final List<ClassNode> topLevelClasses = ast.getClasses();

      for (ClassNode classNode : topLevelClasses) {
        final String topLevel = classNode.getName();
        final String nested = topLevel + "$";
        final SortedSet<String> tail = allClasses.tailSet(topLevel);
        for (Iterator<String> tailItr = tail.iterator(); tailItr.hasNext(); ) {
          String className = tailItr.next();
          if (className.equals(topLevel) || className.startsWith(nested)) {
            tailItr.remove();
            compiledFiles.add(new OutputItem(targetDirectory, className.replace('.', '/') + ".class", fileName));
          }
          else {
            break;
          }
        }
      }
    }
    return compiledFiles;
  }

  @NotNull
  static List<OutputItem> getStubOutputItems(CompilationUnit compilationUnit, final File stubDirectory) {
    final List<OutputItem> compiledFiles = new ArrayList<>();
    compilationUnit.applyToPrimaryClassNodes(new CompilationUnit.PrimaryClassNodeOperation() {
      @Override
      public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
        final String topLevel = classNode.getName();
        String fileName = source.getName();
        if (fileName.startsWith("file:")) {
          try {
            fileName = new URL(fileName).getFile();
          }
          catch (MalformedURLException ignored) {
          }
        }
        OutputItem item = new OutputItem(stubDirectory, topLevel.replace('.', '/') + ".java", fileName);
        if (new File(item.outputPath).exists()) {
          compiledFiles.add(item);
        }
      }

      @SuppressWarnings("RedundantMethodOverride")
      public void doPhaseOperation(CompilationUnit unit) throws CompilationFailedException {
        super.doPhaseOperation(unit);
      }
    });
    return compiledFiles;
  }

  private void addWarnings(ErrorCollector errorCollector) {
    for (int i = 0; i < errorCollector.getWarningCount(); i++) {
      WarningMessage warning = errorCollector.getWarning(i);
      collector.add(new GroovyCompilerMessage(
        GroovyCompilerMessageCategories.WARNING, warning.getMessage(),
        null, -1, -1
      ));
    }
  }

  private void processCompilationException(Exception exception) {
    if (exception instanceof MultipleCompilationErrorsException) {
      MultipleCompilationErrorsException multipleCompilationErrorsException = (MultipleCompilationErrorsException) exception;
      ErrorCollector errorCollector = multipleCompilationErrorsException.getErrorCollector();
      for (int i = 0; i < errorCollector.getErrorCount(); i++) {
        processException(errorCollector.getError(i));
      }
    } else {
      processException(exception, "");
    }
  }

  /** */
  private void processException(Message message) {
    if (message instanceof SyntaxErrorMessage) {
      SyntaxErrorMessage syntaxErrorMessage = (SyntaxErrorMessage) message;
      addErrorMessage(syntaxErrorMessage.getCause());
    } else if (message instanceof ExceptionMessage) {
      ExceptionMessage exceptionMessage = (ExceptionMessage) message;
      processException(exceptionMessage.getCause(), "");
    } else if (message instanceof SimpleMessage) {
      addErrorMessage((SimpleMessage) message);
    } else {
      addMessageWithoutLocation("An unknown error occurred: " + message, true);
    }
  }

  private void processException(Throwable exception, String prefix) {
    if (exception instanceof GroovyRuntimeException) {
      addErrorMessage((GroovyRuntimeException) exception);
      return;
    }

    if (forStubs) {
      collector.add(new GroovyCompilerMessage(
        GroovyCompilerMessageCategories.INFORMATION, GroovyRtConstants.GROOVYC_STUB_GENERATION_FAILED,
        null, -1, -1
      ));
    }

    final StringWriter writer = new StringWriter();
    writer.append(prefix);
    if (!prefix.endsWith("\n")) {
      writer.append("\n\n");
    }
    exception.printStackTrace(new PrintWriter(writer));
    collector.add(new GroovyCompilerMessage(forStubs ? GroovyCompilerMessageCategories.INFORMATION : GroovyCompilerMessageCategories.ERROR, writer.toString(), null, -1, -1));
  }

  private void addMessageWithoutLocation(String message, boolean error) {
    collector.add(new GroovyCompilerMessage(error ? GroovyCompilerMessageCategories.ERROR : GroovyCompilerMessageCategories.WARNING, message, null, -1, -1));
  }

  private static final String LINE_AT = " @ line ";

  private void addErrorMessage(SyntaxException exception) {
    String message = exception.getMessage();
    String justMessage = message.substring(0, message.lastIndexOf(LINE_AT));
    collector.add(new GroovyCompilerMessage(
      GroovyCompilerMessageCategories.ERROR, justMessage,
      exception.getSourceLocator(), exception.getLine(), exception.getStartColumn()
    ));
  }

  private void addErrorMessage(GroovyRuntimeException exception) {
    ASTNode astNode = exception.getNode();
    ModuleNode module = exception.getModule();
    if (module == null) {
      module = findModule(astNode);
    }
    String moduleName = module == null ? "<no module>" : module.getDescription();

    int lineNumber = astNode == null ? -1 : astNode.getLineNumber();
    int columnNumber = astNode == null ? -1 : astNode.getColumnNumber();

    collector.add(new GroovyCompilerMessage(
      GroovyCompilerMessageCategories.ERROR, getExceptionMessage(exception),
      moduleName, lineNumber, columnNumber
    ));
  }

  @NotNull
  private static String getExceptionMessage(GroovyRuntimeException exception) {
    if (exception.getCause() instanceof ClassNotFoundException) {
      String className = exception.getCause().getMessage();
      return "An error occurred while trying to load a required class " + className + "." +
               " Please ensure it's present in the project dependencies. " +
               "See the message and the stack trace below for reference\n\n" + getStackTrace(exception);
    }

    String message = exception.getMessageWithoutLocationText();
    return message == null ? getStackTrace(exception) : message;
  }

  @NotNull
  private static String getStackTrace(GroovyRuntimeException exception) {
    String message;StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    exception.printStackTrace(writer);
    message = stringWriter.getBuffer().toString();
    return message;
  }

  private static ModuleNode findModule(ASTNode node) {
    if (node instanceof ModuleNode) {
      return (ModuleNode)node;
    }
    if (node instanceof ClassNode) {
      return ((ClassNode)node).getModule();
    }
    if (node instanceof AnnotatedNode) {
      ClassNode declaringClass = ((AnnotatedNode)node).getDeclaringClass();
      if (declaringClass != null) {
        return declaringClass.getModule();
      }
    }
    return null;
  }

  private void addErrorMessage(SimpleMessage message) {
    addMessageWithoutLocation(message.getMessage(), true);
  }
}
