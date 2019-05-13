import java.util.Map;
import java.util.Set;

class Reference {

  void m(Map<String, String> map) {
      for (String s : map.values()) {
      System.out.println(s);
    }
  }
}