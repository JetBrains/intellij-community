import org.intellij.lang.annotations.Language;

class C {
  void test() {
    @Language("JAVA")
    String s = "class X{void test(int a, int b, int c, int d) {" +
               "if (a == 0 && (b == 0 || \n" +
               "                   c == 0) && d == 0) {}" +
               "}}";
  }
}