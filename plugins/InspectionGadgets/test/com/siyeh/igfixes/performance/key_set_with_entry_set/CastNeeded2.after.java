import java.util.HashMap;
import java.util.Map;

class CastNeeded2 {

  void m() {
    HashMap<String, String> map2 = new HashMap<>();
    for (Map.Entry<String, String> stringStringEntry: map2.entrySet()) {
      System.out.println((Number) (Object) stringStringEntry.getKey());
      System.out.println(stringStringEntry.getValue());
    }
  }
}