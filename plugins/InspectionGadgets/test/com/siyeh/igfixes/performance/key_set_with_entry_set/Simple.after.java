import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map sortMap = null;
    for (Object o1: sortMap.entrySet()) {
      Object o = ((Map.Entry) o1).getValue();
    }
  }
}
