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
package com.intellij.cvsSupport2.actions.merge;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Stack;

/**
 * @author lesya
 */
public class CvsConflictsParser {
  private static final String RIGHT = "<<<<<<<";
  private static final String LEFT = "=======";
  private static final String END = ">>>>>>>";

  enum State {
    RIGHT, LEFT
  }

  private final StringBuffer myLeftBuffer = new StringBuffer();
  private final StringBuffer myCenterBuffer = new StringBuffer();
  private final StringBuffer myRightBuffer = new StringBuffer();
  private final Stack<State> myStateStack;

  public String getLeftVersion() {
    return myLeftBuffer.toString();
  }

  public String getCenterVersion() {
    return myCenterBuffer.toString();
  }

  public String getRightVersion() {
    return myRightBuffer.toString();
  }

  private CvsConflictsParser() {

    myStateStack = new Stack<State>();
  }

  public static CvsConflictsParser createOn(InputStream merged) throws IOException {
    final CvsConflictsParser result = new CvsConflictsParser();
    result.parseFile(merged);
    return result;
  }

  private void parseFile(final InputStream merged) throws IOException {
    final InputStreamReader isr = new InputStreamReader(merged);
    final BufferedReader br = new BufferedReader(isr);
    try {
      String line;
      while ((line = br.readLine()) != null) {
        String cutLine = findMarkerAndWriteTail(line, RIGHT);
        if (cutLine != null) {
          processRightMarker(cutLine);
          continue;
        }

        cutLine = findMarkerAndWriteTail(line, LEFT);
        if (cutLine != null) {
          processLeftMarker(cutLine);
          continue;
        }

        cutLine = findMarkerAndWriteTail(line, END);
        if (cutLine != null) {
          processEndMarker(cutLine);
          continue;
        }

        appendToMainOrCurrent(line);
      }
    }
    finally {
      br.close();
    }
  }

  private void appendToMainOrCurrent(final String line) {
    if (myStateStack.isEmpty()) {
      appendLine(line);
    } else {
      appendToCurrentBuffer(line);
    }
  }

  @Nullable
  private String findMarkerAndWriteTail(final String s, final String marker) {
    final int idx = s.indexOf(marker);
    if (idx == -1) {
      return null;
    }
    final String startFragment = s.substring(0, idx);
    if (startFragment.length() > 0) {
      appendToMainOrCurrent(startFragment);
    }
    return s.substring(idx);
  }

  private void processEndMarker(final String line) {
    if (myStateStack.isEmpty()) {
      appendLine(line);
    }
    else {
      myStateStack.pop();
      if (!myStateStack.isEmpty()) {
        appendToCurrentBuffer(line);
      }
    }
  }

  private void processLeftMarker(final String line) {
    if (myStateStack.isEmpty()) {
      appendLine(line);
    }
    else if (myStateStack.peek() == State.LEFT) {
      myStateStack.pop();
      myStateStack.push(State.RIGHT);
      if (myStateStack.size() > 1) {
        appendToCurrentBuffer(line);
      }
    }
    else {
      appendToCurrentBuffer(line);
    }
  }

  private void processRightMarker(final String line) {
    if (myStateStack.isEmpty()) {
      myStateStack.push(State.LEFT);
    }
    else {
      myStateStack.push(State.LEFT);
      appendToCurrentBuffer(line);
    }
  }

  private void appendToCurrentBuffer(final String line) {
    if (myStateStack.get(0) == State.RIGHT) {
      appendLine(line, myRightBuffer);
    } else {
      appendLine(line, myLeftBuffer);
    }
  }

  private void appendLine(final String line) {
    appendLine(line, myLeftBuffer);
    appendLine(line, myCenterBuffer);
    appendLine(line, myRightBuffer);
  }

  private static void appendLine(final String line, final StringBuffer buffer) {
    if (buffer.length() > 0) {
      buffer.append("\n");
    }
    buffer.append(line);

  }

}
