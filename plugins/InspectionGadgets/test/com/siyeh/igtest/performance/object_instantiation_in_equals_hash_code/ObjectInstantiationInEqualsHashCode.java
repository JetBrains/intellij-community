import java.util.Arrays;

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
class X {

  Object a;
  int b;

  public boolean equals(Object o) {
    throw new UnsupportedOperationException("asdf" + a);
  }

  public int hashCode() {
    assert a != null : "check " + b;
    return a.hashCode() + b;
  }
}