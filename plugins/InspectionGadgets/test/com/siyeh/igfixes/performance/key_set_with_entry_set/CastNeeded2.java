import java.util.HashMap;

class CastNeeded2 {

  void m() {
    HashMap<String, String> map2 = new HashMap<>();
    for (Object o : map2.keySet()<caret>) {
      System.out.println((Number) o);
      System.out.println(map2.get(o));
    }
  }
}