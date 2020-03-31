import org.jetbrains.annotations.*;

class X {
  static native String message(@PropertyKey(resourceBundle = "MyBundle") String key, Object... params);

  void test(@Nls(capitalization = Nls.Capitalization.Title) String title) {

  }

  void test2(@Nls(capitalization = Nls.Capitalization.Sentence) String title) {

  }

  void main(int x) {
    test(<warning descr="String 'hello world' is not properly capitalized. It should have title capitalization">message("property.lowercase")</warning>);
    test(message("property.titlecase"));
    test(message("property.unknown"));
    test(message("property.parameterized", "World"));
    test(message("property.parameterized", "world")); // not supported
    test(message("property.choice.title", x));
    test(<warning descr="String 'Hello {0,choice,0#World|1#universe}' is not properly capitalized. It should have title capitalization">message("property.choice.mixed", x)</warning>);
    test(<warning descr="String 'Hello {0,choice,0#world|1#universe}' is not properly capitalized. It should have title capitalization">message("property.choice.lower", x)</warning>);

    test2(<warning descr="String 'hello world' is not properly capitalized. It should have sentence capitalization">message("property.lowercase")</warning>);
    test2(<warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">message("property.titlecase")</warning>);
    test2(message("property.unknown"));
    test2(message("property.parameterized", "World"));
    test2(message("property.parameterized", "world"));
    test2(<warning descr="String 'Hello {0,choice,0#World|1#Universe}' is not properly capitalized. It should have sentence capitalization">message("property.choice.title", x)</warning>);
    test2(<warning descr="String 'Hello {0,choice,0#World|1#universe}' is not properly capitalized. It should have sentence capitalization">message("property.choice.mixed", x)</warning>);
    test2(message("property.choice.lower", x));
  }
}