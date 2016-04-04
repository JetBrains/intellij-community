import java.util.Collection;
import java.util.Iterator;

public class UnboundWildcard {

  void m(Collection<?> c) {

      for (Object aC : c) {
          final String s = (String) aC;
      }
  }
}