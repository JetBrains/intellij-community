import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map<String, String> sortMap = null;
    for (String key : sortMap.key<caret>Set()) {
      System.out.println("Value is: " + sortMap.get(key));
      String val = sortMap.get(key);
      System.out.println(val);
    }
  }
}
