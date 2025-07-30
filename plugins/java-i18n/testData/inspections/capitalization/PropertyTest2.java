import java.lang.annotation.*;
import java.util.*;
import java.util.function.*;
import org.jetbrains.annotations.*;
import static java.lang.annotation.ElementType.*;

class Y {
  static native String message(@PropertyKey(resourceBundle = "MyBundle") String key, Object... params);
  
  void test(@Nls(capitalization = Nls.Capitalization.Title) String title) {
    
  }

  void main(int x) {
    test(<warning descr="String 'hello world' is not properly capitalized. It should have title capitalization"><caret>Y.message("property.lowercase")</warning>);
  }
}