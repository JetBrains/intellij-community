// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.actions.merge;

import com.intellij.util.containers.Stack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @author lesya
 */
public class CvsConflictsParser {

  private static final String LEFT = "<<<<<<< ";
  private static final String RIGHT = "=======";
  private static final String END = ">>>>>>> ";
  private static final int LENGTH = RIGHT.length();

  private enum State {
    RIGHT, LEFT
  }

  private final StringBuilder myLeftBuffer = new StringBuilder();
  private final StringBuilder myCenterBuffer = new StringBuilder();
  private final StringBuilder myRightBuffer = new StringBuilder();
  private final Stack<State> myStateStack = new Stack<>();

  public String getLeftVersion() {
    return myLeftBuffer.toString();
  }

  public String getCenterVersion() {
    return myCenterBuffer.toString();
  }

  public String getRightVersion() {
    return myRightBuffer.toString();
  }

  private CvsConflictsParser() {}

  public static CvsConflictsParser createOn(InputStream merged) throws IOException {
    final CvsConflictsParser result = new CvsConflictsParser();
    result.parseFile(merged);
    return result;
  }

  private void parseFile(final InputStream merged) throws IOException {
    final BufferedReader br = new BufferedReader(new InputStreamReader(merged, StandardCharsets.UTF_8));
    try {
      for (String line; (line = br.readLine()) != null; ) {
        if (!processLeftMarker(line) && !processRightMarker(line) && !processEndMarker(line)) {
          appendToMainOrCurrent(line);
        }
      }
    }
    finally {
      br.close();
    }
  }

  private boolean processLeftMarker(String line) {
    final int idx = line.lastIndexOf(LEFT);
    if (idx < 0) {
      return false;
    }
    if (myStateStack.isEmpty()) {
      final String fragment = line.substring(0, idx);
      if (!fragment.isEmpty()) {
        appendToMainOrCurrent(fragment);
      }
    }
    else {
      appendToMainOrCurrent(line);
    }
    myStateStack.push(State.LEFT);
    return true;
  }

  private boolean processRightMarker(String line) {
    if (!line.endsWith(RIGHT)) {
      return false;
    }
    if (!myStateStack.isEmpty() && myStateStack.peek() == State.LEFT) {
      if (myStateStack.size() > 1) {
        appendToMainOrCurrent(line);
      }
      else {
        final String fragment = line.substring(0, line.length() - LENGTH);
        if (!fragment.isEmpty()) {
          appendToMainOrCurrent(fragment);
        }
      }
      myStateStack.pop();
      myStateStack.push(State.RIGHT);
    }
    else {
      appendToMainOrCurrent(line);
    }
    return true;
  }

  private boolean processEndMarker(String line) {
    final int idx = line.lastIndexOf(END);
    if (idx < 0) {
      return false;
    }
    if (!myStateStack.isEmpty()) {
      if (myStateStack.size() > 1) {
        appendToMainOrCurrent(line);
      }
      else {
        final String fragment = line.substring(0, idx);
        if (!fragment.isEmpty()) {
          appendToMainOrCurrent(fragment);
        }
      }
      myStateStack.pop();
    }
    else {
      appendToMainOrCurrent(line);
    }
    return true;
  }

  private void appendToMainOrCurrent(String line) {
    if (myStateStack.isEmpty()) {
      append(line, myLeftBuffer);
      append(line, myCenterBuffer);
      append(line, myRightBuffer);
    } else {
      if (myStateStack.get(0) == State.RIGHT) {
        append(line, myRightBuffer);
      } else {
        append(line, myLeftBuffer);
      }
    }
  }

  private static void append(String line, StringBuilder out) {
    if (out.length() > 0) {
      out.append("\n");
    }
    out.append(line);
  }
}
