import java.util.*;

class EqualsWithItself_ignoreNonFinalClasses {

  void objectTest(Object o) {
    org.junit.jupiter.api.Assertions.assertEquals(o, o);
  }

  void stringTest(String s) {
    <error descr="Cannot return a value from a method with void result type">return org.junit.jupiter.api.Assertions.<warning descr="'assertEquals()' called on itself">assertEquals</warning>(s, s);</error>
  }

  boolean stringEquals(String s) {
    return s.equalsIgnoreCase(s);
  }

  void primitiveTest(int i) {
    org.junit.jupiter.api.Assertions.<warning descr="'assertEquals()' called on itself">assertEquals</warning>(i, i);
  }

  void customClassTest(FinalClass finalClass) {
    org.junit.jupiter.api.Assertions.assertEquals(finalClass, finalClass);
  }

  private static final class FinalClass{
  }
}