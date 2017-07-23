import java.util.Map;

public class CastNeeded {

  public void testGetOrDefault(Map<String, Number> map, String key) {
      if (map.containsKey(key)) f(map.get(key));
      else f((Object) null);
  }

  void f(char[] cs) {}
  void f(String n) {}
  void f(Object o) {}
}