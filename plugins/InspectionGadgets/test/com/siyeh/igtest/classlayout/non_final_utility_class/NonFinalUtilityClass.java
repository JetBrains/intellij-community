package com.siyeh.igtest.classlayout.non_final_utility_class;


class <warning descr="Utility class 'NonFinalUtilityClass' is not 'final'">NonFinalUtilityClass</warning> {

  public static void foo() {}
}
final class FinalUtilityClass {
  public static void foo() {}
}
abstract class NoUtilityClass {
  public static void foo() {}
}
class ConcreteNoUtilityClass {
  public static void foo() {}
}

class NonFinalClass {
  public NonFinalClass() {}
  public static void foo() {}
}

class TestClass extends NonFinalClass {
  public TestClass() {}
}