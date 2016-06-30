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
}