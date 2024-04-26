class Example {
  static void main(String[] args) {
    def a = <warning descr="Conditional expression with identical branches">args[0]</warning> ? new String[10] : new String[10];
  }
}
