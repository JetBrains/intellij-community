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
}