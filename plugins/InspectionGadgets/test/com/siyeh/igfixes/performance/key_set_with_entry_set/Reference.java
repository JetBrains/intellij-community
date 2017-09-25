import java.util.Map;
import java.util.Set;

class Reference {

  void m(Map<String, String> map) {
    Set<String> keys = map.keySet();
    for (String key : <caret>keys) {
      System.out.println(map.get(key));
    }
  }
}