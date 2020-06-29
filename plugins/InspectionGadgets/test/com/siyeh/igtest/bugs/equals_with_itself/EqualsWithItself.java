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

  static class Outer {
    class Inner extends Outer {
      void test() {
        if (equals(Outer.this)) { // equals called on itself

        }
      }
    }
  }
}