package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import java.util.Arrays;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.02.2008
 */
public class MyPair<Item, Aspect> {
  private Item first;
  private Aspect second;

  public MyPair(Item first, Aspect second) {
    this.first = first;
    this.second = second;
  }

  public void setFirst(Item first) {
    this.first = first;
  }

  public void setSecond(Aspect second) {
    this.second = second;
  }

  public final Item getFirst() {
    return first;
  }

  public final Aspect getSecond() {
    return second;
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
