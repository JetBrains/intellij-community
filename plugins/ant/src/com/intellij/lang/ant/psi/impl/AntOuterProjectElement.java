package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * element for wrapping xml out of the project tag, that can be xml prolog or smth
 */
public class AntOuterProjectElement extends AntElementImpl {
  private final int myStartOffset;
  private final String myText;

  public AntOuterProjectElement(final AntElement parent, final int startOffset, final String text) {
    super(parent, null);
    myStartOffset = startOffset;
    myText = text;
  }

  @NonNls
  public String toString() {
    return "XmlText: " + myText;
  }

  public boolean isPhysical() {
    return getParent().isPhysical();
  }

  public TextRange getTextRange() {
    return new TextRange(myStartOffset, myStartOffset + myText.length());
  }

  public int getTextLength() {
    return myText.length();
  }

  public int getTextOffset() {
    return myStartOffset;
  }

  public String getText() {
    return myText;
  }

  @NotNull
  public char[] textToCharArray() {
    return myText.toCharArray();
  }

  public boolean textContains(char c) {
    return myText.indexOf(c, 0) >= 0;
  }
}
