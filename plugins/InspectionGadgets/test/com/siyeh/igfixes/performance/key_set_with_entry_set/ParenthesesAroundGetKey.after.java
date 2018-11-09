import java.util.Map;

abstract class B {
  void m(Map<String, String> m) {
    for (Map.Entry<String, String> stringStringEntry : m.entry<caret>Set()) {
      if (stringStringEntry.getKey().startsWith("abc")) {
        String s1 = stringStringEntry.getValue();
        System.out.println(s1);
      }
    }
  }
}
