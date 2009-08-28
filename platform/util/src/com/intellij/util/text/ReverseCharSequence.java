package com.intellij.util.text;

/**
 * @author cdr
 */
public class ReverseCharSequence implements CharSequence{
  private final CharSequence mySequence;

  public ReverseCharSequence(CharSequence sequence) {
    mySequence = sequence;
  }

  public int length() {
    return mySequence.length();
  }

  public char charAt(int index) {
    return mySequence.charAt(mySequence.length()-index-1);
  }

  public CharSequence subSequence(int start, int end) {
    return new ReverseCharSequence(mySequence.subSequence(start, end));
  }
}
