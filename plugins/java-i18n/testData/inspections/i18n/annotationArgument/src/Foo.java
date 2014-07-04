@interface Language {
  @org.jetbrains.annotations.NonNls
  java.lang.String value();
}

class Foo {
    void foo() {
        @Language("abcdefgh")
        String s;
    }
}