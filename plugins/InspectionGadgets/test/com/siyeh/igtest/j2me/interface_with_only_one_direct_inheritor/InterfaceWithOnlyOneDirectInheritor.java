package com.siyeh.igtest.j2me.interface_with_only_one_direct_inheritor;

public interface InterfaceWithOnlyOneDirectInheritor {}
class Inheritor implements InterfaceWithOnlyOneDirectInheritor {}
interface InterfaceWithoutInheritor {}
interface InterfaceWithTwoInheritors {}
class Inheritor1 implements InterfaceWithTwoInheritors {}
class Inheritor2 implements InterfaceWithTwoInheritors {}