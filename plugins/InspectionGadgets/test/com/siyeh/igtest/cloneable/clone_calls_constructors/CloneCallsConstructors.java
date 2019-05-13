package com.siyeh.igtest.cloneable.clone_calls_constructors;

import java.util.*;

class CloneCallsConstructors implements Cloneable {

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new <warning descr="'clone()' creates new 'CloneCallsConstructors' instances">CloneCallsConstructors</warning>();
  }
}
class One {
  @Override
  public Object clone() throws CloneNotSupportedException {
    return new <warning descr="'clone()' creates new 'One' instances">One</warning>();
  }
}
final class Two implements Cloneable {
  @Override
  public Object clone() throws CloneNotSupportedException {
    return new Two();
  }
}
class Three implements Cloneable {
  @Override
  public final Object clone() throws CloneNotSupportedException {
    return new Three();
  }
}
class Four implements Cloneable {
  private String[] s = {"four"};

  @Override
  public Four clone() {
    try {
      Four clone = (Four)super.clone();
      clone.s = new <warning descr="'clone()' creates new String[] array">String</warning>[1];
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }
}
class Five implements Cloneable {
  private NonCloneable x = new NonCloneable();

  @Override
  public Five clone() {
    try {
      Five clone = (Five)super.clone();
      clone.x = new NonCloneable();
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  class NonCloneable {}
}
