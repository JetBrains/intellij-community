package com.siyeh.igtest.cloneable.clone_calls_constructors;

class CloneCallsConstructors implements Cloneable {

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return new <warning descr="'clone()' creates new 'CloneCallsConstructors' instances">CloneCallsConstructors</warning>();
  }
}