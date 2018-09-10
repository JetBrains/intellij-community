import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map sortMap = null;
    for (Object o1 : (sortMap.entry<caret>Set())) {
      Object o = ((Map.Entry) o1).getValue();
    }
  }
}
