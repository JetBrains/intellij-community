package com.intellij.cvsSupport2.actions.merge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Stack;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 13, 2005
 * Time: 11:05:20 PM
 * To change this template use File | Settings | File Templates.
 */


public class CvsConflictsParser {
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
    String line;
    while ((line = br.readLine()) != null) {
      if (line.startsWith("<<<<<<<")) {
        processRightMarker(line);
      }
      else if (line.startsWith("=======")) {
        processLeftMarker(line);
      }
      else if (line.startsWith(">>>>>>>")) {
        processEndMarker(line);
      }
      else if (myStateStack.isEmpty()) {
        appendLine(line);
      } else {
        appendToCurrentBuffer(line);
      }
    }

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
