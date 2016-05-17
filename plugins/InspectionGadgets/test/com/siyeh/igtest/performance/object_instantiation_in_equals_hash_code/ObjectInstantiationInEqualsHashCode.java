import java.util.Arrays;
import java.util.Comparator;

class ObjectInstantiationInEqualsHashCode {

  Object a;
  int b;

  public boolean equals(Object o) {
    ObjectInstantiationInEqualsHashCode other = (ObjectInstantiationInEqualsHashCode)o;
    return Arrays.equals(new <warning descr="Object instantiation inside 'equals()'">Object</warning>[] {a, b}, new <warning descr="Object instantiation inside 'equals()'">Object</warning>[] {other.a, other.b});
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