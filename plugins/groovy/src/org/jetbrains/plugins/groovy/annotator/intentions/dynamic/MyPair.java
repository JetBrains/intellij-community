package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import java.util.Arrays;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.02.2008
 */
public class MyPair  {
  public String first = null;
  public String second = null;

  public MyPair() {
  }

  public MyPair(String first, String second) {
    this.first = first;
    this.second = second;
  }

  public void setFirst(String first) {
    this.first = first;
  }
  
  public void setSecond(String second) {
    this.second = second;
  }

  public final int hashCode() {
    int hashCode = 0;
    if (first != null) {
      hashCode += hashCode(first);
    }
    if (second != null) {
      hashCode += hashCode(second);
    }
    return hashCode;
  }

  private static int hashCode(final Object o) {
    return (o instanceof Object[]) ? Arrays.hashCode((Object[]) o) : o.hashCode();
  }

  public String toString() {
    return "<" + first + "," + second + ">";
  }
}
