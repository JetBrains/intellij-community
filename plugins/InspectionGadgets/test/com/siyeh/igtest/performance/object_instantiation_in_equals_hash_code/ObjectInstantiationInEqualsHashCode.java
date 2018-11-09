import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

class ObjectInstantiationInEqualsHashCode {

  Object a;
  int b;

  public boolean equals(Object o) {
    ObjectInstantiationInEqualsHashCode other = (ObjectInstantiationInEqualsHashCode)o;
    return Arrays.equals(new <warning descr="Object instantiation inside 'equals()'">Object</warning>[] {a, <warning descr="Object instantiation inside 'equals()' (autoboxing)">b</warning>}, new <warning descr="Object instantiation inside 'equals()'">Object</warning>[] {other.a, <warning descr="Object instantiation inside 'equals()' (autoboxing)">other.b</warning>});
  }

  public int hashCode() {
    return (<warning descr="Object instantiation inside 'hashCode()'">a + ", " + b</warning>).hashCode();
  }
}
class X implements Comparable<X>, Comparator<String> {

  Object a;
  int b;

  public boolean equals(Object o) {
    throw new UnsupportedOperationException("asdf" + a);
  }

  public int hashCode() {
    assert a != null : "check " + b;
    return a.hashCode() + b;
  }

  public int compareTo(X x) {
    new <warning descr="Object instantiation inside 'compareTo()'">Object</warning>();
    return 0;
  }

  public int compare(String s1, String s2) {
    new <warning descr="Object instantiation inside 'compare()'">Object</warning>();
    return 0;
  }
}
class Y {

  public java.util.List<Object> fooList = new java.util.ArrayList<>();

  @Override
  public int hashCode() {
    Integer i = 1;
    i = <warning descr="Object instantiation inside 'hashCode()' (autoboxing)">128</warning>;
    Short.valueOf((short) 1);
    <warning descr="Object instantiation inside 'hashCode()'">Short.valueOf((short) 128)</warning>;
    Byte.valueOf((byte) 1); // nope
    Long.valueOf(1);
    <warning descr="Object instantiation inside 'hashCode()'">Long.valueOf(128)</warning>;
    Boolean.valueOf(true); // nope
    Character.valueOf('a');
    <warning descr="Object instantiation inside 'hashCode()'">Character.valueOf('П')</warning>;
    Float.<warning descr="Object instantiation inside 'hashCode()'">valueOf</warning>((float) 1.0);
    Double.<warning descr="Object instantiation inside 'hashCode()'">valueOf</warning>(1.0);
    <warning descr="Object instantiation inside 'hashCode()' (autoboxing)">i</warning>++;
    int j = 1;
    j++;
    int[] is = <warning descr="Object instantiation inside 'hashCode()'">{127}</warning>;
    Integer.valueOf(is[0]);
    int hashCode = 7;
    java.util.Arrays.<warning descr="Object instantiation inside 'hashCode()'">asList</warning>();
    for (Object fooElement : <warning descr="Object instantiation inside 'hashCode()' (iterator)">fooList</warning>) {
      hashCode = 31 * hashCode + (fooElement == null ? 0 : fooElement.hashCode());
    }
    Pattern.<warning descr="Object instantiation inside 'hashCode()'">compile</warning>("regex");
    return hashCode;
  }

  void someOtherMethod() {
    Integer i = 1;
    Short.valueOf((short) 1);
    Byte.valueOf((byte) 1); // nope
    Long.valueOf(1);
    Boolean.valueOf(true); // nope
    Character.valueOf('a');
    Float.valueOf((float) 1.0);
    Double.valueOf(1.0);
    i++;
    int j = 1;
    j++;
    int[] is = {j};
    int hashCode = 7;
    java.util.Arrays.asList();
    for (Object fooElement : fooList) {
      hashCode = 31 * hashCode + (fooElement == null ? 0 : fooElement.hashCode());
    }
  }

}
class Autoboxing {
  Boolean b = Boolean.FALSE;

  public int hashCode() {
    Boolean b1 = true;
    Byte b2 = 8;
    return !b ? 0 : 1;
  }
}