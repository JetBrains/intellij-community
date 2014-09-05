import java.util.HashMap;

public class LocalVariable {

  void m() {
    final HashMap<String, String> map = new HashMap();// comment
      map.put("a", "b");
      map.put("a", "b");
      map.put("a", "b");
      map.put("a", "b");

  }
}