// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
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
 */
class EclipseOutputParser {
  private final @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String myBuilderName;
  private final ModuleChunk myChunk;

  EclipseOutputParser(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String builderName, ModuleChunk chunk) {
    myBuilderName = builderName;
    myChunk = chunk;
  }

  private static final String PROB_SEPARATOR = "----------\n";

  List<CompilerMessage> parseMessages(String input) throws IOException {
    if (input.contains("The type groovy.lang.GroovyObject cannot be resolved. It is indirectly referenced from required .class files")) {
      return Collections.singletonList(new CompilerMessage(
        myBuilderName, BuildMessage.Kind.ERROR,
        GroovyJpsBundle.message("no.groovy.library.0", myChunk.representativeTarget().getModule().getName())
      ));
    }

    List<CompilerMessage> parsedMessages = new ArrayList<>();

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
  private CompilerMessage parseMessage(@NlsSafe String msgText) {
    // message should look like this:
    //        1. WARNING in /Users/andrew/git-repos/foo/src/main/java/packAction.java (at line 47)
    //            public abstract class AbstractScmTagAction extends TaskAction implements BuildBadgeAction {
    //                                  ^^^^^^^^^^^^^^^^^^^^

    // But there will also be messages contributed from annotation processors that will look non-normal
    int dotIndex = msgText.indexOf('.');
    BuildMessage.Kind kind;
    boolean isNormal = false;
    if (dotIndex > 0) {
      if (msgText.substring(dotIndex).startsWith(". WARNING")) {
        kind = BuildMessage.Kind.WARNING;
        isNormal = true;
        dotIndex += ". WARNING in ".length();
      } else if (msgText.substring(dotIndex).startsWith(". ERROR")) {
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
