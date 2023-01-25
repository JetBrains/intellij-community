import java.util.*;

class EqualsWithItself {

  boolean foo(Object o) {
    return o.<warning descr="'equals()' called on itself">equals</warning>(((o)));
  }

  boolean withGetter() {
    return getValue().<warning descr="'equals()' called on itself">equals</warning>(getValue());
  }

  boolean withMethodCall() {
    return build().equals(build());
  }

  void selfEquality() {
    boolean b = <warning descr="'equals()' called on itself">equals</warning>(this);
  }

  private Integer value = 1;
  public Integer getValue() {
    return value;
  }

  public Object build() {
    return new Object();
  }

  boolean string(String s) {
    return s.<warning descr="'equalsIgnoreCase()' called on itself">equalsIgnoreCase</warning>(s);
  }

  int compareTo(String s) {
    return s.<warning descr="'compareTo()' called on itself">compareTo</warning>(s);
  }

  int compareToIgnoreCase(String s) {
    return s.<warning descr="'compareToIgnoreCase()' called on itself">compareToIgnoreCase</warning>(s);
  }

  boolean safe(String a, String b) {
    return a.equals(b) && a.equalsIgnoreCase(b) && a.compareTo(b) == 0;
  }

  void wrappers(int a, boolean b) {
    Byte.<warning descr="'compare()' called on itself">compare</warning>((byte)a, (byte)a);
    Byte.<warning descr="'compareUnsigned()' called on itself">compareUnsigned</warning>((byte)a, (byte)a);
    Short.<warning descr="'compare()' called on itself">compare</warning>((short)a, (short)a);
    Short.<warning descr="'compareUnsigned()' called on itself">compareUnsigned</warning>((short)a, (short)a);
    Integer.<warning descr="'compare()' called on itself">compare</warning>(a, a);
    Integer.<warning descr="'compareUnsigned()' called on itself">compareUnsigned</warning>(a, a);
    Long.<warning descr="'compare()' called on itself">compare</warning>((long)a, (long)a);
    Long.<warning descr="'compareUnsigned()' called on itself">compareUnsigned</warning>((long)a, (long)a);
    Double.<warning descr="'compare()' called on itself">compare</warning>((double)a, (double)a);
    Float.<warning descr="'compare()' called on itself">compare</warning>((float)a, (float)a);
    Boolean.<warning descr="'compare()' called on itself">compare</warning>(b, b);
    Character.<warning descr="'compare()' called on itself">compare</warning>((char)a, (char)a);
  }

  void more(String[] ss) {
    Arrays.<warning descr="'equals()' called on itself">equals</warning>(ss, ss);
    Arrays.<warning descr="'deepEquals()' called on itself">deepEquals</warning>(ss, ss);
    Objects.<warning descr="'equals()' called on itself">equals</warning>(ss, ss);
    Objects.<warning descr="'deepEquals()' called on itself">deepEquals</warning>(ss, ss);
    Comparator c = (o1, o2) -> 0;
    c.<warning descr="'compare()' called on itself">compare</warning>(ss, ss);
  }

  void test(String arg) {
    org.junit.jupiter.api.Assertions.<warning descr="'assertEquals()' called on itself">assertEquals</warning>(arg, arg);
    org.junit.jupiter.api.Assertions.<warning descr="'assertNotEquals()' called on itself">assertNotEquals</warning>(arg, arg);
    org.junit.Assert.<warning descr="'assertSame()' called on itself">assertSame</warning>(arg, arg);
    org.assertj.core.api.Assertions.assertThat(arg).<warning descr="'isEqualTo()' called on itself">isEqualTo</warning>(arg);
    org.assertj.core.api.Assertions.assertThat(arg).anotherTest(arg).<warning descr="'isEqualTo()' called on itself">isEqualTo</warning>(arg);
    Object o1 = new Object();
    Object o2 = new Object();
    org.junit.Assert.assertSame(o1, o2);

    UpperComplexObject complexObject1 = new UpperComplexObject();
    UpperComplexObject complexObject2 = new UpperComplexObject();

    org.junit.Assert.<warning descr="'assertSame()' called on itself">assertSame</warning>(complexObject1.a1.i, complexObject1.a1.i);
    org.junit.Assert.assertSame(complexObject1.a2.i, complexObject1.a1.i);
    org.junit.Assert.assertSame(complexObject2.a1.i, complexObject1.a1.i);
    org.assertj.core.api.Assertions.<error descr="Cannot resolve method 'assertArrayEquals' in 'Assertions'">assertArrayEquals</error>(new int[]{1,2}, new int[]{1,2});
  }

  private static class UpperComplexObject{
    private ComplexObject a1 = new ComplexObject();
    private ComplexObject a2 = new ComplexObject();
  }

  private static class ComplexObject{
    private int i;
  }

  static class Outer {
    class Inner extends Outer {
      void test() {
        if (equals(Outer.this)) { // equals called on itself

        }
      }
    }
    class Inner2 {
      void test() {
        if (<warning descr="'equals()' called on itself">equals</warning>(Inner2.this)) {

        }
      }
    }
  }
}