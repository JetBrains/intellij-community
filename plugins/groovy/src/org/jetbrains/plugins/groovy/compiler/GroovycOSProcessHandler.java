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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.plugins.groovy.compiler.rt.GroovycRunner;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */
public class GroovycOSProcessHandler extends OSProcessHandler {
  private Set<TranslatingCompiler.OutputItem> compiledFilesNames = new HashSet<TranslatingCompiler.OutputItem>();
  private Set<File> toRecompileFiles = new HashSet<File>();
  private List<CompilerMessage> compilerMessages = new ArrayList<CompilerMessage>();
  private StringBuffer unparsedOutput = new StringBuffer();
  private CompileContext myContext;

  public GroovycOSProcessHandler(CompileContext context, Process process, String s) {
    super(process, s);
    myContext = context;
  }

  public void notifyTextAvailable(final String text, final Key outputType) {
    super.notifyTextAvailable(text, outputType);
//    System.out.println("text: " + text);

    parseOutput(text);
  }

  private final StringBuffer outputBuffer = new StringBuffer();

  public void parseOutput(String text) {
    text = text.trim();
    if (text != null && !"".equals(text)) {
      outputBuffer.append(text);

      //compiled start marker have to be in the beginning on each string
      if (outputBuffer.indexOf(GroovycRunner.COMPILED_START) != -1) {
        unparsedOutput.setLength(0);

        if (!(outputBuffer.indexOf(GroovycRunner.COMPILED_END) != -1)) {
          return;
        }

        {
          text = handleOutputBuffer(GroovycRunner.COMPILED_START, GroovycRunner.COMPILED_END);

          StringTokenizer tokenizer = new StringTokenizer(text, GroovycRunner.SEPARATOR, false);

          String token = "";
          /*
          * output path
          * source file
          * output root directory
          */

          String outputPath = "";
          String sourceFile = "";
          String outputRootDirectory = "";

          if (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            outputPath = token;
          }

          if (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            sourceFile = token;
          }

          if (tokenizer.hasMoreTokens()) {
            token = tokenizer.nextToken();
            outputRootDirectory = token;
          }

          try {
            myContext.getProgressIndicator().setText(sourceFile);
            compiledFilesNames.add(getOutputItem(outputPath, sourceFile, outputRootDirectory));
          } catch (InvocationTargetException e) {
            e.printStackTrace();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }

      } else if (outputBuffer.indexOf(GroovycRunner.TO_RECOMPILE_START) != -1) {
        unparsedOutput.setLength(0);
        if (!(outputBuffer.indexOf(GroovycRunner.TO_RECOMPILE_END) != -1)) {
          return;
        }

        if (outputBuffer.indexOf(GroovycRunner.TO_RECOMPILE_END) != -1) {
          text = handleOutputBuffer(GroovycRunner.TO_RECOMPILE_START, GroovycRunner.TO_RECOMPILE_END);

          myContext.getProgressIndicator().setText(text);
          toRecompileFiles.add(new File(text));
        }
      }

      /* Cathegory
      * Message
      * Url
      * Linenum
      * Colomnnum
      */

      else if (outputBuffer.indexOf(GroovycRunner.MESSAGES_START) != -1) {
        unparsedOutput.setLength(0);
        if (!(outputBuffer.indexOf(GroovycRunner.MESSAGES_END) != -1)) {
          return;
        }

        text = handleOutputBuffer(GroovycRunner.MESSAGES_START, GroovycRunner.MESSAGES_END);

        String cathegory = "";
        String message = "";
        String url = "";
        String linenum = "";
        String colomnnum = "";
        String token;

        final StringTokenizer compilerMessageTokenizer = new StringTokenizer(text, GroovycRunner.SEPARATOR, false);

        if (compilerMessageTokenizer.hasMoreTokens()) {
          token = compilerMessageTokenizer.nextToken();
          cathegory = token;
        }

        if (compilerMessageTokenizer.hasMoreTokens()) {
          token = compilerMessageTokenizer.nextToken();
          message = token;
        }

        if (compilerMessageTokenizer.hasMoreTokens()) {
          token = compilerMessageTokenizer.nextToken();
          url = token;
        }

        if (compilerMessageTokenizer.hasMoreTokens()) {
          token = compilerMessageTokenizer.nextToken();
          linenum = token;
        }

        if (compilerMessageTokenizer.hasMoreTokens()) {
          token = compilerMessageTokenizer.nextToken();
          colomnnum = token;
        }

        int linenumInt;
        int colomnnumInt;

        try {
          linenumInt = Integer.parseInt(linenum);
          colomnnumInt = Integer.parseInt(colomnnum);
        } catch (NumberFormatException e) {
          e.printStackTrace();
          linenumInt = 0;
          colomnnumInt = 0;
        }

        myContext.getProgressIndicator().setText(url);

        compilerMessages.add(new CompilerMessage(cathegory, message, url, linenumInt, colomnnumInt));
      } else {
        if (outputBuffer.indexOf("Exception") != -1) unparsedOutput.append(outputBuffer);
        outputBuffer.setLength(0);
      }
    }
  }

  private String handleOutputBuffer(String START_MARKER, String END_MARKER) {
    String text;
    text = outputBuffer.substring(
        outputBuffer.indexOf(START_MARKER) + START_MARKER.length(),
        outputBuffer.indexOf(END_MARKER)).toString();

    outputBuffer.delete(
        outputBuffer.indexOf(START_MARKER),
        outputBuffer.indexOf(END_MARKER) + END_MARKER.length());

    return text;
  }

  private TranslatingCompiler.OutputItem getOutputItem(final String outputPath, final String sourceFile, final String outputRootDir) throws InvocationTargetException, InterruptedException {

    final VirtualFile sourceVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(sourceFile));

    return new TranslatingCompiler.OutputItem() {
      public String getOutputPath() {
        return outputPath;
      }

      public VirtualFile getSourceFile() {
        return sourceVirtualFile;
      }

      public String getOutputRootDirectory() {
        return outputRootDir;
      }
    };
  }

  public Set<TranslatingCompiler.OutputItem> getSuccessfullyCompiled() {
    return Collections.unmodifiableSet(compiledFilesNames);
  }

  public Set<File> getToRecompileFiles() {
    return toRecompileFiles;
  }

  public List<CompilerMessage> getCompilerMessages() {
    return compilerMessages;
  }

  public StringBuffer getUnparsedOutput() {
    return unparsedOutput;
  }
}
