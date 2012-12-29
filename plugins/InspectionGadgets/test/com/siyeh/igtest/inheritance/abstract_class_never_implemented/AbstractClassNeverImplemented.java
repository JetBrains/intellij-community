package com.siyeh.igtest.inheritance.abstract_class_never_implemented;

public abstract class AbstractClassNeverImplemented {}
abstract class AbstractClassWithOnlyOneDirectInheritor {}
class Inheritor extends AbstractClassWithOnlyOneDirectInheritor {}
abstract class AbstractClassWithTwoInheritors {}
class Inheritor1 extends AbstractClassWithTwoInheritors {}
class Inheritor2 extends AbstractClassWithTwoInheritors {}