import java.util.Map;
import java.util.Set;

class Reference {

  void m(Map<String, String> map) {
      for (Map.Entry<String, String> stringStringEntry : map.entrySet()) {
      System.out.println(stringStringEntry.getValue());
    }
  }
}