package com.siyeh.igtest.inheritance.interface_never_implemented;

public interface InterfaceNeverImplemented {}
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