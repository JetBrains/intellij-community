/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.jps.incremental.groovy;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerMessageCategories;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */
public class GroovycOutputParser {
  private static final String GROOVY_COMPILER_IN_OPERATION = "Groovy compiler in operation...";
  public static final String GRAPE_ROOT = "grape.root";
  private final List<OutputItem> myCompiledItems = new ArrayList<OutputItem>();
  private final List<CompilerMessage> compilerMessages = new ArrayList<CompilerMessage>();
  private final StringBuffer stdErr = new StringBuffer();
  private final ModuleChunk myChunk;
  private final CompileContext myContext;

  public GroovycOutputParser(ModuleChunk chunk, CompileContext context) {
    myChunk = chunk;
    myContext = context;
  }

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GroovycOSProcessHandler");
  private int myExitCode;

  public void notifyTextAvailable(final String text, final Key outputType) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Received from groovyc " + outputType + ": " + text);
    }

    if (outputType == ProcessOutputTypes.SYSTEM) {
      return;
    }

    if (outputType == ProcessOutputTypes.STDERR && !isSafeStderr(text)) {
      stdErr.append(StringUtil.convertLineSeparators(text));
      return;
    }


    parseOutput(text);
  }

  private static boolean isSafeStderr(String line) {
    return line.startsWith("SLF4J:") || line.startsWith("Picked up JAVA_TOOL_OPTIONS");
  }

  private final StringBuffer outputBuffer = new StringBuffer();

  private void updateStatus(@NotNull String status) {
    myContext.processMessage(new ProgressMessage(status + " [" + myChunk.getPresentableShortName() + "]"));
  }

  private void parseOutput(String text) {
    final String trimmed = text.trim();

    if (trimmed.startsWith(GroovyRtConstants.PRESENTABLE_MESSAGE)) {
      updateStatus(trimmed.substring(GroovyRtConstants.PRESENTABLE_MESSAGE.length()));
      return;
    }

    if (GroovyRtConstants.CLEAR_PRESENTABLE.equals(trimmed)) {
      updateStatus(GROOVY_COMPILER_IN_OPERATION);
      return;
    }


    if (StringUtil.isNotEmpty(text)) {
      outputBuffer.append(text);

      //compiled start marker have to be in the beginning on each string
      if (outputBuffer.indexOf(GroovyRtConstants.COMPILED_START) != -1) {
        if (outputBuffer.indexOf(GroovyRtConstants.COMPILED_END) == -1) {
          return;
        }

        final String compiled = handleOutputBuffer(GroovyRtConstants.COMPILED_START, GroovyRtConstants.COMPILED_END);
        final List<String> list = splitAndTrim(compiled);
        String outputPath = list.get(0);
        String sourceFile = list.get(1);

        OutputItem item = new OutputItem(outputPath, sourceFile);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Output: " + item);
        }
        myCompiledItems.add(item);

      }
      else if (outputBuffer.indexOf(GroovyRtConstants.MESSAGES_START) != -1) {
        if (outputBuffer.indexOf(GroovyRtConstants.MESSAGES_END) == -1) {
          return;
        }

        text = handleOutputBuffer(GroovyRtConstants.MESSAGES_START, GroovyRtConstants.MESSAGES_END);

        List<String> tokens = splitAndTrim(text);
        LOG.assertTrue(tokens.size() > 4, "Wrong number of output params");

        String category = tokens.get(0);
        String message = tokens.get(1);
        String url = tokens.get(2);
        String lineNum = tokens.get(3);
        String columnNum = tokens.get(4);

        int lineInt;
        int columnInt;

        try {
          lineInt = Integer.parseInt(lineNum);
          columnInt = Integer.parseInt(columnNum);
        }
        catch (NumberFormatException e) {
          LOG.error(e);
          lineInt = 0;
          columnInt = 0;
        }

        BuildMessage.Kind kind = category.equals(GroovyCompilerMessageCategories.ERROR)
                                 ? BuildMessage.Kind.ERROR
                                 : category.equals(GroovyCompilerMessageCategories.WARNING)
                                   ? BuildMessage.Kind.WARNING
                                   : BuildMessage.Kind.INFO;

        CompilerMessage compilerMessage = new CompilerMessage("Groovyc", kind, message, url, -1, -1, -1, lineInt, columnInt);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Message: " + compilerMessage);
        }
        addCompilerMessage(compilerMessage);
      }
    }
  }

  void addCompilerMessage(CompilerMessage compilerMessage) {
    compilerMessages.add(compilerMessage);
  }

  private String handleOutputBuffer(String startMarker, String endMarker) {
    final int start = outputBuffer.indexOf(startMarker);
    final int end = outputBuffer.indexOf(endMarker);
    if (start > end) {
      throw new AssertionError("Malformed Groovyc output: " + outputBuffer.toString());
    }

    String text = outputBuffer.substring(start + startMarker.length(), end);

    outputBuffer.delete(start, end + endMarker.length());

    return text.trim();
  }

  private static List<String> splitAndTrim(String compiled) {
    return ContainerUtil.map(StringUtil.split(compiled, GroovyRtConstants.SEPARATOR), new Function<String, String>() {
      public String fun(String s) {
        return s.trim();
      }
    });
  }

  public List<OutputItem> getSuccessfullyCompiled() {
    return myCompiledItems;
  }

  public boolean shouldRetry() {
    if (myExitCode != 0) {
      LOG.debug("Non-zero exit code");
      return true;
    }
    for (CompilerMessage message : compilerMessages) {
      if (message.getKind() == BuildMessage.Kind.ERROR) {
        LOG.debug("Error message: " + message);
        return true;
      }
      if (message.getMessageText().contains(GroovyRtConstants.GROOVYC_STUB_GENERATION_FAILED)) {
        LOG.debug("Stub failed message: " + message);
        return true;
      }
    }
    if (getStdErr().length() > 0) {
      LOG.debug("Non-empty stderr: '" + getStdErr() + "'");
      return true;
    }
    return false;
  }

  public List<CompilerMessage> getCompilerMessages() {
    ArrayList<CompilerMessage> messages = new ArrayList<CompilerMessage>(compilerMessages);
    final StringBuffer unparsedBuffer = getStdErr();
    if (unparsedBuffer.length() != 0) {
      String msg = unparsedBuffer.toString();
      if (msg.contains(GroovyRtConstants.NO_GROOVY)) {
        messages.add(reportNoGroovy());
      } else {
        messages.add(new CompilerMessage("Groovyc", BuildMessage.Kind.INFO, msg));
      }

    }

    if (myExitCode != 0) {
      for (CompilerMessage message : messages) {
        if (message.getKind() == BuildMessage.Kind.ERROR) {
          return messages;
        }
      }
      messages.add(new CompilerMessage("Groovyc", BuildMessage.Kind.ERROR, "Internal groovyc error: code " + myExitCode));
    }

    return messages;
  }

  @NotNull
  CompilerMessage reportNoGroovy() {
    String moduleName = myChunk.representativeTarget().getModule().getName();
    return new CompilerMessage("", BuildMessage.Kind.ERROR,
                               "Cannot compile Groovy files: no Groovy library is defined for module '" + moduleName + "'");
  }

  public StringBuffer getStdErr() {
    return stdErr;
  }

  public static File fillFileWithGroovycParameters(final String outputDir,
                                                   final Collection<String> changedSources,
                                                   Collection<String> finalOutputs,
                                                   Map<String, String> class2Src,
                                                   @Nullable final String encoding,
                                                   List<String> patchers,
                                                   String classpath) throws IOException {
    File tempFile = FileUtil.createTempFile("ideaGroovyToCompile", ".txt", true);

    final Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile)));
    try {
      writer.write(classpath);
      writer.write("\n");

      for (String file : changedSources) {
        writer.write(GroovyRtConstants.SRC_FILE + "\n");
        writer.write(file);
        writer.write("\n");
      }

      writer.write("class2src\n");
      for (Map.Entry<String, String> entry : class2Src.entrySet()) {
        writer.write(entry.getKey() + "\n");
        writer.write(entry.getValue() + "\n");
      }
      writer.write(GroovyRtConstants.END + "\n");

      writer.write(GroovyRtConstants.PATCHERS + "\n");
      for (String patcher : patchers) {
        writer.write(patcher + "\n");
      }
      writer.write(GroovyRtConstants.END + "\n");
      if (encoding != null) {
        writer.write(GroovyRtConstants.ENCODING + "\n");
        writer.write(encoding + "\n");
      }
      writer.write(GroovyRtConstants.OUTPUTPATH + "\n");
      writer.write(outputDir);
      writer.write("\n");
      writer.write(GroovyRtConstants.FINAL_OUTPUTPATH + "\n");
      writer.write(StringUtil.join(finalOutputs, File.pathSeparator));
      writer.write("\n");
    }
    finally {
      writer.close();
    }
    return tempFile;
  }

  void notifyFinished(int exitCode) {
    myExitCode = exitCode;
  }

  public void onContinuation() {
    myCompiledItems.clear();
    compilerMessages.clear();
    stdErr.setLength(0);
    outputBuffer.setLength(0);
  }

  static class OutputItem {
    public final String outputPath;
    public final String sourcePath;

    public OutputItem(String outputPath, String sourceFileName) {
      this.outputPath = outputPath;
      sourcePath = sourceFileName;
    }

    @Override
    public String toString() {
      return "OutputItem{" +
             "outputPath='" + outputPath + '\'' +
             ", sourcePath='" + sourcePath + '\'' +
             '}';
    }
  }

}
