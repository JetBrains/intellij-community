package test;

import com.intellij.util.concurrency.annotations.RequiresReadLock;

public class PolymorphicTest {
  public void testMethod() {
    Base base = new Derived();
    base.polymorphicMethod();
  }
}

abstract class Base {
  public abstract void polymorphicMethod();
}

class Derived extends Base {
  @Override
  @RequiresReadLock
  public void polymorphicMethod() {}
}