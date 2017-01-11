import java.util.Map;

public class CastNeeded {

  public void testGetOrDefault(Map<String, Number> map, String key) {
    f(((((map.containsKey(key) ? ((map.get(key))) : (null)<caret>)))));
  }

  void f(char[] cs) {}
  void f(String n) {}
  void f(Object o) {}
}