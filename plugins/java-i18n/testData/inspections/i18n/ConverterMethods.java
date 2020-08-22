import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nls;

class MyTest {
  static String internEmpty(String s) {
    return s.isEmpty() ? "" : s;
  }
  
  static String notNullize(String s) {
    return notNullize(s, "");
  }
  
  static String notNullize(String s, String def) {
    return s == null ? def : s;
  }
  
  static <T> T reqNonNull(T t) {
    if (t == null) throw new NullPointerException();
    return t;
  }
  
  void test(String s) {
    consume(internEmpty(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>));
    consume(notNullize(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>));
    consume(notNullize(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>, <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>));
    consume(reqNonNull(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>));
    consume(MyTest.internEmpty(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>));
    consume(MyTest.notNullize(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>));
    consume(MyTest.notNullize(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>, <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>));
    
    consume(internEmpty(notNullize(<warning descr="Reference to non-localized string is used where localized string is expected">s</warning>)));
    String s1 = <warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>;
    consume(internEmpty(notNullize(s1)));
    consumeOk(internEmpty(notNullize(s1)));
  }
  
  void consume(@Nls String s) {}
  void consumeOk(@NonNls String s) {}
}