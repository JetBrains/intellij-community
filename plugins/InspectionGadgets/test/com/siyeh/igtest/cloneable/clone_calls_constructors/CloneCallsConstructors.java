package com.siyeh.igtest.cloneable.clone_calls_constructors;

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
