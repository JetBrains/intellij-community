import java.util.Iterator;
import java.util.Map;

abstract class B {
  {
    Map sortMap = null;
    for (Object entry : (sortMap.entry<caret>Set())) {
      Object o = ((Map.Entry) entry).getValue();
    }
  }
}
