import org.jetbrains.annotations.Nls;
import java.util.*;


class MyTest {
  Map<String, @Nls String> map = new HashMap<>();
  @Nls String test(String s) {
    return map.computeIfAbsent(s, x -> <warning descr="Hardcoded string literal: \"value\"">"value"</warning>);
  }
}