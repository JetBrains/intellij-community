/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapted from org.codehaus.groovy.eclipse.compiler.GroovyEclipseCompiler, part of maven groovy-eclipse-compiler plugin.
 *
 * The source is distributed under Eclipse Public License (http://www.eclipse.org/legal/epl-v10.html, eclipse_license.txt).
 *
 * @author peter
 */
class EclipseOutputParser {
  private final String myBuilderName;
  private final ModuleChunk myChunk;

  public EclipseOutputParser(String builderName, ModuleChunk chunk) {
    myBuilderName = builderName;
    myChunk = chunk;
  }

  private static final String PROB_SEPARATOR = "----------\n";

  List<CompilerMessage> parseMessages(String input) throws IOException {
    if (input.contains("The type groovy.lang.GroovyObject cannot be resolved. It is indirectly referenced from required .class files")) {
      return Collections.singletonList(new CompilerMessage(myBuilderName, BuildMessage.Kind.ERROR,
                                                           "Cannot compile Groovy files: no Groovy library is defined for module '" +
                                                           myChunk.representativeTarget().getModule().getName() +
                                                           "'"));
    }

    List<CompilerMessage> parsedMessages = new ArrayList<CompilerMessage>();

    String[] msgs = StringUtil.convertLineSeparators(input).split(PROB_SEPARATOR);
    for (String msg : msgs) {
      if (msg.length() > 1) {
        // add the error bean
        CompilerMessage message = parseMessage(msg);
        if (message != null) {
          parsedMessages.add(message);
        }
        else {
          // assume that there are one or more non-normal messages here
          // All messages start with <num>. ERROR or <num>. WARNING
          String[] extraMsgs = msg.split("\n");
          StringBuilder sb = new StringBuilder();
          for (String extraMsg : extraMsgs) {
            if (extraMsg.indexOf(". WARNING") > 0 || extraMsg.indexOf(". ERROR") > 0) {
              handleCurrentMessage(parsedMessages, sb);
              sb = new StringBuilder("\n").append(extraMsg).append("\n");
            }
            else {
              if (!PROB_SEPARATOR.equals(extraMsg)) {
                sb.append(extraMsg).append("\n");
              }
            }
          }
          handleCurrentMessage(parsedMessages, sb);
        }
      }
    }
    return parsedMessages;
  }

  private void handleCurrentMessage(final List<CompilerMessage> parsedMessages, final StringBuilder sb) {
    if (sb.length() > 0) {
      ContainerUtil.addIfNotNull(parsedMessages, parseMessage(sb.toString()));
    }
  }

  @Nullable
  private CompilerMessage parseMessage(String msgText) {
    // message should look like this:
    //        1. WARNING in /Users/andrew/git-repos/foo/src/main/java/packAction.java (at line 47)
    //            public abstract class AbstractScmTagAction extends TaskAction implements BuildBadgeAction {
    //                                  ^^^^^^^^^^^^^^^^^^^^

    // But there will also be messages contributed from annotation processors that will look non-normal
    int dotIndex = msgText.indexOf('.');
    BuildMessage.Kind kind;
    boolean isNormal = false;
    if (dotIndex > 0) {
      if (msgText.substring(dotIndex, dotIndex + ". WARNING".length()).equals(". WARNING")) {
        kind = BuildMessage.Kind.WARNING;
        isNormal = true;
        dotIndex += ". WARNING in ".length();
      } else if (msgText.substring(dotIndex, dotIndex + ". ERROR".length()).equals(". ERROR")) {
        kind = BuildMessage.Kind.ERROR;
        isNormal = true;
        dotIndex += ". ERROR in ".length();
      } else {
        kind = BuildMessage.Kind.INFO;
      }
    } else {
      kind = BuildMessage.Kind.INFO;
    }

    int firstNewline = msgText.indexOf('\n');
    String firstLine = firstNewline > 0 ? msgText.substring(0, firstNewline) : msgText;
    String rest = firstNewline > 0 ? msgText.substring(firstNewline+1).trim() : "";

    if (isNormal) {
      try {
        int parenIndex = firstLine.indexOf(" (");
        String file = firstLine.substring(dotIndex, parenIndex);
        int line = Integer.parseInt(firstLine.substring(parenIndex + " (at line ".length(), firstLine.indexOf(')')));
        int lastLineIndex = rest.lastIndexOf("\n");
        return new CompilerMessage(myBuilderName, kind, rest.substring(lastLineIndex + 1), file, -1, -1, -1, line, -1);
      }
      catch (RuntimeException ignore) {
      }
    }

    if (msgText.trim().matches("(\\d)+ problem(s)? \\((\\d)+ (error|warning)(s)?\\)")) {
      return null;
    }

    return new CompilerMessage(myBuilderName, kind, msgText);
  }

}
