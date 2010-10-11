package org.jetbrains.javafx.build;

import com.intellij.compiler.CompilerException;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.JavaFxUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxCompilerHandler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.javafx.build.JavaFxCompilerHandler");

  private final CompileContext myContext;
  private final Sdk mySdk;
  private final Project myProject;
  private String myCompilerOutputPath;
  private List<File> myTempFiles = new ArrayList<File>();

  public JavaFxCompilerHandler(CompileContext context, Sdk sdk, Project project) {
    myContext = context;
    mySdk = sdk;
    myProject = project;
  }

  public void runCompiler(final CompileContext context, final Module module, final List<VirtualFile> files) throws CompilerException {
    LOG.debug("JavaFX SDK version: " + mySdk.getVersionString());
    final List<String> command = new ArrayList<String>();
    command.add(mySdk.getHomePath() + "/bin/javafxc");
    command.add("-cp");
    command.add(createClasspath(context, module));
    myCompilerOutputPath = JavaFxUtil.getCompilerOutputPath(module);
    if (myCompilerOutputPath != null) {
      command.add("-d");
      command.add(myCompilerOutputPath);
    }
    command.add(createSourcePathCommand(files));
    LOG.debug("Compilation command: " + command);

    final ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    try {
      final Process process = builder.start();
      readProcessOutput(process);
    }
    catch (IOException e) {
      myContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
  }

  private String createClasspath(final CompileContext context, final Module module) throws CompilerException {
    final ModuleChunk chunk =
      new ModuleChunk((CompileContextEx)context, new Chunk<Module>(module), Collections.<Module, List<VirtualFile>>emptyMap());
    final String compilationClasspath = chunk.getCompilationClasspath();
    LOG.debug("Classpath: " + compilationClasspath);
    return compilationClasspath;
  }

  private String createSourcePathCommand(final List<VirtualFile> files) throws CompilerException {
    try {
      final File tempFile = createTempFile("idea_javafxc_src");
      final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(tempFile)));
      try {
        for (VirtualFile file : files) {
          writer.println(PathUtil.getLocalPath(file));
          LOG.debug("Source file: " + file.getPath());
        }
      }
      finally {
        writer.close();
      }
      return ("@" + tempFile.getAbsolutePath());
    }
    catch (IOException e) {
      throw new CompilerException(e.getMessage(), e);
    }
  }

  private File createTempFile(final String prefix) throws IOException {
    final File tempFile = FileUtil.createTempFile(prefix, ".tmp");
    tempFile.deleteOnExit();
    myTempFiles.add(tempFile);
    return tempFile;
  }

  @Nullable
  public String getCompilerOutputPath() {
    return myCompilerOutputPath;
  }

  // TODO:

  @NotNull
  public Collection<TranslatingCompiler.OutputItem> getOutputItems() {
    return Collections.emptyList();
  }

  public void compileFinished() {
    FileUtil.asyncDelete(myTempFiles);
    myTempFiles.clear();
  }

  private void readProcessOutput(final Process process) throws IOException {
    try {
      final InputStreamReader reader = new InputStreamReader(process.getInputStream());
      try {
        final StringBuilder builder = new StringBuilder();
        final char[] buf = new char[2048];
        int read = reader.read(buf);
        while (read >= 0) {
          final String output = new String(buf, 0, read);
          builder.append(output);
          read = reader.read(buf);
        }
        handleOutput(builder.toString());
      }
      finally {
        cancel(process);
        reader.close();
      }
    }
    catch (IOException e) {
      myContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
  }

  private static void cancel(Process process) {
    if (process != null) {
      process.destroy();
    }
  }

  private void handleOutput(final String output) {
    // TODO: better errors parsing
    LOG.debug("Compiler output: " + output);
    final Pattern errorPattern = Pattern.compile("((?:[A-Z]:\\\\)?[\\.\\w\\\\/]*):(\\d*): (.*)");
    final String[] outputLines = output.split("[\\n\\r]");
    for (String outputLine : outputLines) {
      if (StringUtil.isEmptyOrSpaces(outputLine)) {
        continue;
      }
      final Matcher matcher = errorPattern.matcher(outputLine);
      if (matcher.matches()) {
        final String file = matcher.group(1);
        final String line = matcher.group(2);
        String message = matcher.group(3);
        final String column = "0";

        CompilerMessageCategory messageCategory = CompilerMessageCategory.ERROR;
        if (StringUtil.startsWith(message, "warning:")) {
          messageCategory = CompilerMessageCategory.WARNING;
          message = message.substring(9);
        }
        final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
        final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(file);
        final VirtualFile relativeFile = VfsUtil.findRelativeFile(file, projectFileIndex.getSourceRootForFile(virtualFile));
        myContext.addMessage(messageCategory, message, relativeFile != null ? relativeFile.getUrl() : null,
                             line != null ? Integer.parseInt(line) : 0, column != null ? Integer.parseInt(column) : 0);
        LOG.debug("Message: " + message);
      }
      else {
        //myContext.addMessage(CompilerMessageCategory.INFORMATION, output, null, -1, -1);
      }
    }
  }
}
