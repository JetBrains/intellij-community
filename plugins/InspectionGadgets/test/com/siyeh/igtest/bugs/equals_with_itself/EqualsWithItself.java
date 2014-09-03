class EqualsWithItself {

  boolean foo(Object o) {
    return o.<warning descr="Identical qualifier and argument to 'equals()' call">equals</warning>(((o)));
  }

  boolean withGetter() {
    return getValue().<warning descr="Identical qualifier and argument to 'equals()' call">equals</warning>(getValue());
  }

  boolean withMethodCall() {
    return build().equals(build());
  }

  private Integer value = 1;
  public Integer getValue() {
    return value;
  }

  public Object build() {
    return new Object();
  }
}