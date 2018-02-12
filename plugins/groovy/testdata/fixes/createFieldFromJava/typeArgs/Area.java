import java.util.*;
class Area {
  void foo(Map<String, String> s){}

  void bar() {
    foo(new A().fie<caret>ld);
  }

}