import org.jetbrains.annotations.Nls;
import java.util.*;


class MyTest {
  Map<String, <error descr="'@Nls' not applicable to type use">@Nls</error> String> map = new HashMap<>();
  @Nls String test(String s) {
    return map.computeIfAbsent(s, x -> <warning descr="Hardcoded string literal: \"value\"">"value"</warning>);
  }
}