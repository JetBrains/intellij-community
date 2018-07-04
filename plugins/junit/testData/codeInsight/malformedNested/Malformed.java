class A {
  @org.junit.jupiter.api.Nested
  static class <warning descr="Only non-static nested classes can serve as @Nested test classes.">B</warning> {}
}