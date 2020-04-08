public class Annotation {
  @interface NotNull {}

  void x() {
    @<warning descr="report annotation only once">NotNull</warning> Integer[] integers = {};
  }
}