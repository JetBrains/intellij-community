package com.siyeh.igtest.serialization.serializable_with_unconstructable_ancestor;


import java.io.*;

public class <warning descr="'SerializableWithUnconstructableAncestor' has a non-serializable ancestor 'NonserializableGrandParent' without no-arg constructor">SerializableWithUnconstructableAncestor</warning> extends SerializableParent implements Serializable
{
    public SerializableWithUnconstructableAncestor(int arg, int foo)
    {
        super(arg, foo);
    }

}
class <warning descr="'SerializableParent' has a non-serializable ancestor 'NonserializableGrandParent' without no-arg constructor">SerializableParent</warning> extends NonserializableGrandParent implements Serializable
{
  private int m_foo;

  public SerializableParent(int arg, int foo)
  {
    super(arg);
    m_foo = foo;
  }
}
class NonserializableGrandParent
{
  private int m_arg;
  public NonserializableGrandParent(int arg)
  {
    super();
    m_arg = arg;
    bar(m_arg);
  }

  private void bar(int arg)
  {
  }
}
class A {

  A(String s) {}

  private Object writeReplace() throws ObjectStreamException {
    return null;
  }
}
class B extends A implements Serializable {

  B(String s) {
    super(s);
  }
}

