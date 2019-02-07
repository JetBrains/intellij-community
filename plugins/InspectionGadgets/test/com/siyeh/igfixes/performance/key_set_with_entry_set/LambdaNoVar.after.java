import java.util.HashMap;
import java.util.Map;

public class EntryIterationBug {
  Map<String, Integer> reMap(Map<String, Integer> anotherMap) {
    Map<String, Integer> map = new HashMap<>();
    anotherMap<caret>.forEach((key, integer) -> {
        if (integer % 2 == 0) {
            map.put(key, 1);
        } else {
            map.put(key, 2);
        }
    });
    return map;
  }
}