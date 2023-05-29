import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map<String, String> sortMap = null;
    for (String val : sortMap.values<caret>()) {
      System.out.println("Value is: " + val);
        System.out.println(val);
    }
  }
}
