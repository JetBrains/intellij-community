import org.jetbrains.annotations.NonNls;

@NonNls
@interface ClassName {}

@interface JustAnno {}

class NlsMeta {
  void foo1(@ClassName String s) {}
  
  void foo2(@JustAnno String s) {}
  
  void foo3(@ClassName @JustAnno String s) {}
  
  void test() {
    foo1("foo bar baz");
    foo2(<warning descr="Hardcoded string literal: \"foo bar baz\"">"foo bar baz"</warning>);
    foo3("foo bar baz");
  }
}