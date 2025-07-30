import java.lang.annotation.*;
import java.util.*;
import java.util.function.*;
import org.jetbrains.annotations.*;
import static java.lang.annotation.ElementType.*;

class X {
  static native String message(@PropertyKey(resourceBundle = "MyBundle") String key, Object... params);
  
  static native Supplier<String> messagePointer(@PropertyKey(resourceBundle = "MyBundle") String key, Object... params);

  void test(@Nls(capitalization = Nls.Capitalization.Title) String title) {
    
  }

  void test2(@Nls(capitalization = Nls.Capitalization.Sentence) String title) {

  }
  
  void testSupplier(Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> title) {
    
  }
  
  String getBlahBlah() {
    return this.message("property.titlecase");
  }

  void main(int x) {
    test(<warning descr="String 'hello world' is not properly capitalized. It should have title capitalization"><caret>message("property.lowercase")</warning>);
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
    test2(<warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">getBlahBlah()</warning>);

    test2(message("property.sentence.with.quote"));

    test(<warning descr="String '{0,choice,0#No|1#{0}} {0,choice,0#occurrences|1#occurrence|2#occurrences} found so far' is not properly capitalized. It should have title capitalization">message("property.choice.sentence.start", x)</warning>);
    test2(message("property.choice.sentence.start", x));
    
    test(message("property.titlecase.html"));
    test2(<warning descr="String '<html><b>Hello</b> World</html>' is not properly capitalized. It should have sentence capitalization">message("property.titlecase.html")</warning>);
    
    testSupplier(<warning descr="String 'Hello World' is not properly capitalized. It should have sentence capitalization">this.messagePointer("property.titlecase")</warning>);
    testSupplier(messagePointer("property.parameterized", "foo"));

    test(message("property.icu4j.title", 10));
    test2(<warning descr="String 'Generate Code with {0, plural, one {Foo} other {Bar}}' is not properly capitalized. It should have sentence capitalization">message("property.icu4j.title", 10)</warning>);

    test(message("property.with.underscore.mnemonic"));
  }
}