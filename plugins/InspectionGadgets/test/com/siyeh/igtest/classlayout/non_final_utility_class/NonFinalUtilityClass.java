package com.siyeh.igtest.classlayout.non_final_utility_class;


class NonFinalUtilityClass {

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