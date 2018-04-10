package com.siyeh.igtest.inheritance.interface_never_implemented;

import com.intellij.test.Ignore;

public interface <warning descr="Interface 'InterfaceNeverImplemented' has no concrete subclass">InterfaceNeverImplemented</warning> {}
interface InterfaceWithOnlyOneDirectInheritor {}
class Inheritor implements InterfaceWithOnlyOneDirectInheritor {}
interface InterfaceWithTwoInheritors {}
class Inheritor1 implements InterfaceWithTwoInheritors {}
class Inheritor2 implements InterfaceWithTwoInheritors {}

interface SAM {
  void foo();
}

class LambdaCall {
  {
    SAM sam = () -> {};
  }
}
@Ignore
interface NotImplemented {}