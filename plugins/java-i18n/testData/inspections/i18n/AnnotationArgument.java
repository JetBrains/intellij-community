@interface Language {
  @org.jetbrains.annotations.NonNls
  java.lang.String value();
}

class Foo {
    void foo() {
        @Language("abcdefgh")
        String s;
        @Language("abcdefgh" + "")
        String s1;
    }
}