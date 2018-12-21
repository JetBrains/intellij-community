import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map sortMap = null;
    for (Object entry : sortMap.entrySet()) {
      Object o = ((Map.Entry) entry).getValue();
    }
  }
}
