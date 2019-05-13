import org.jetbrains.annotations.NonNls;

// "Annotate variable 'foo' as @NonNls" "true"
class X {
  void test() {
    @NonNls String foo = "bar";
    String foo1 = foo.toUpperCase();
  }
}