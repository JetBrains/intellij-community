package org.jetbrains.protocolReader;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class TextOutput {
  public static final char[] EMPTY_CHARS = new char[0];
  private int identLevel;
  private final static int indentGranularity = 2;
  private char[][] indents = {EMPTY_CHARS};
  private boolean justNewlined;
  private final StringBuilder out;

  public TextOutput(StringBuilder out) {
    this.out = out;
  }

  public StringBuilder getOut() {
    return out;
  }

  public TextOutput indentIn() {
    ++identLevel;
    if (identLevel >= indents.length) {
      // Cache a new level of indentation string.
      char[] newIndentLevel = new char[identLevel * indentGranularity];
      Arrays.fill(newIndentLevel, ' ');
      char[][] newIndents = new char[indents.length + 1][];
      System.arraycopy(indents, 0, newIndents, 0, indents.length);
      newIndents[identLevel] = newIndentLevel;
      indents = newIndents;
    }
    return this;
  }

  public TextOutput indentOut() {
    --identLevel;
    return this;
  }

  public TextOutput newLine() {
    out.append('\n');
    justNewlined = true;
    return this;
  }

  public TextOutput append(double value) {
    maybeIndent();
    out.append(value);
    return this;
  }

  public TextOutput append(boolean value) {
    maybeIndent();
    out.append(value);
    return this;
  }

  public TextOutput append(int value) {
    maybeIndent();
    out.append(value);
    return this;
  }

  public TextOutput append(char c) {
    maybeIndent();
    out.append(c);
    return this;
  }

  public void append(char[] s) {
    maybeIndent();
    out.append(s);
  }

  public TextOutput append(CharSequence s) {
    maybeIndent();
    out.append(s);
    return this;
  }

  public TextOutput append(CharSequence s, int start) {
    maybeIndent();
    out.append(s, start, s.length());
    return this;
  }

  public TextOutput openBlock() {
    openBlock(true);
    return this;
  }

  public void openBlock(boolean addNewLine) {
    space().append('{');
    if (addNewLine) {
      newLine();
    }
    indentIn();
  }

  public void closeBlock() {
    indentOut().newLine().append('}');
  }

  public TextOutput comma() {
    return append(',').append(' ');
  }

  public TextOutput space() {
    return append(' ');
  }

  public TextOutput semi() {
    return append(';');
  }

  public TextOutput doc(@Nullable String description) {
    if (description == null) {
      return this;
    }
    return append("/**").newLine().append(" * ").append(description).newLine().append(" */").newLine();
  }

  public TextOutput quoute(CharSequence s) {
    return append('"').append(s).append('"');
  }

  public void maybeIndent() {
    if (justNewlined) {
      out.append(indents[identLevel]);
      justNewlined = false;
    }
  }
}
