import java.util.Collection;
import java.util.Iterator;

public class UnboundWildcard {

  void m(Collection<?> c) {
    final Iterator<?> it = c.iterator();

    while<caret> (it.hasNext()) {
      final String s = (String)it.next();
    }
  }
}