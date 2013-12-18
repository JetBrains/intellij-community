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

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerMessageCategories;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.*;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */
public class GroovycOSProcessHandler extends BaseOSProcessHandler {
  public static final String GROOVY_COMPILER_IN_OPERATION = "Groovy compiler in operation...";
  public static final String GRAPE_ROOT = "grape.root";
  private final List<OutputItem> myCompiledItems = new ArrayList<OutputItem>();
  private final Set<File> toRecompileFiles = new HashSet<File>();
  private final List<CompilerMessage> compilerMessages = new ArrayList<CompilerMessage>();
  private final StringBuffer stdErr = new StringBuffer();

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GroovycOSProcessHandler");
  private final Consumer<String> myStatusUpdater;

  public GroovycOSProcessHandler(Process process, Consumer<String> statusUpdater) {
    super(process, null, null);
    myStatusUpdater = statusUpdater;
  }

  public void notifyTextAvailable(final String text, final Key outputType) {
    super.notifyTextAvailable(text, outputType);

    if (LOG.isDebugEnabled()) {
      LOG.debug("Received from groovyc " + outputType + ": " + text);
    }

    if (outputType == ProcessOutputTypes.SYSTEM) {
      return;
    }

    if (outputType == ProcessOutputTypes.STDERR) {
      stdErr.append(StringUtil.convertLineSeparators(text));
      return;
    }


    parseOutput(text);
  }

  private final StringBuffer outputBuffer = new StringBuffer();

  protected void updateStatus(@Nullable String status) {
    myStatusUpdater.consume(status == null ? GROOVY_COMPILER_IN_OPERATION : status);
  }

  private void parseOutput(String text) {
    final String trimmed = text.trim();

    if (trimmed.startsWith(GroovyRtConstants.PRESENTABLE_MESSAGE)) {
      updateStatus(trimmed.substring(GroovyRtConstants.PRESENTABLE_MESSAGE.length()));
      return;
    }

    if (GroovyRtConstants.CLEAR_PRESENTABLE.equals(trimmed)) {
      updateStatus(null);
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
      else if (outputBuffer.indexOf(GroovyRtConstants.TO_RECOMPILE_START) != -1) {
        if (outputBuffer.indexOf(GroovyRtConstants.TO_RECOMPILE_END) != -1) {
          String url = handleOutputBuffer(GroovyRtConstants.TO_RECOMPILE_START, GroovyRtConstants.TO_RECOMPILE_END);
          toRecompileFiles.add(new File(url));
        }
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
        compilerMessages.add(compilerMessage);
      }
    }
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

  public Set<File> getToRecompileFiles() {
    return toRecompileFiles;
  }

  public boolean shouldRetry() {
    if (getProcess().exitValue() != 0) {
      LOG.debug("Non-zero exit code");
      return true;
    }
    for (CompilerMessage message : compilerMessages) {
      if (message.getKind() == BuildMessage.Kind.ERROR) {
        LOG.debug("Error message: " + message);
        return true;
      }
    }
    if (getStdErr().length() > 0) {
      LOG.debug("Non-empty stderr: '" + getStdErr() + "'");
      return true;
    }
    return false;
  }

  public List<CompilerMessage> getCompilerMessages(String moduleName) {
    ArrayList<CompilerMessage> messages = new ArrayList<CompilerMessage>(compilerMessages);
    final StringBuffer unparsedBuffer = getStdErr();
    if (unparsedBuffer.length() != 0) {
      String msg = unparsedBuffer.toString();
      if (msg.contains(GroovyRtConstants.NO_GROOVY)) {
        msg = "Cannot compile Groovy files: no Groovy library is defined for module '" + moduleName + "'";
      }

      messages.add(new CompilerMessage("Groovyc", BuildMessage.Kind.INFO, msg));
    }

    final int exitValue = getProcess().exitValue();
    if (exitValue != 0) {
      for (CompilerMessage message : messages) {
        if (message.getKind() == BuildMessage.Kind.ERROR) {
          return messages;
        }
      }
      messages.add(new CompilerMessage("Groovyc", BuildMessage.Kind.ERROR, "Internal groovyc error: code " + exitValue));
    }

    return messages;
  }

  public StringBuffer getStdErr() {
    return stdErr;
  }

  public static File fillFileWithGroovycParameters(final String outputDir,
                                                   final Collection<String> changedSources,
                                                   String finalOutput,
                                                   Map<String, String> class2Src, @Nullable final String encoding, List<String> patchers) throws IOException {
    File tempFile = FileUtil.createTempFile("ideaGroovyToCompile", ".txt", true);

    final Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile)));
    try {
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
      writer.write(finalOutput);
      writer.write("\n");
    }
    finally {
      writer.close();
    }
    return tempFile;
  }

  public static GroovycOSProcessHandler runGroovyc(Process process, Consumer<String> updater) {
    GroovycOSProcessHandler processHandler = new GroovycOSProcessHandler(process, updater);

    processHandler.startNotify();
    processHandler.waitFor();
    return processHandler;
  }

  public static class OutputItem {
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
