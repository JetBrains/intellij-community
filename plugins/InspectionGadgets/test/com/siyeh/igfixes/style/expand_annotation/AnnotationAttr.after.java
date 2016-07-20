class T {
  @interface B { String[] value(); }
  @interface C { B value(); }

  @C(value = @B(value = "v"))
  void foo() {
  }
}