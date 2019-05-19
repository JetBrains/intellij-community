import java.util.Collection;
import java.util.Iterator;

public class UnboundWildcard {

  void m(Collection<?> c) {

      for (Object o : c) {
          final String s = (String) o;
      }
  }
}