import java.util.*;

public class MyTest {
  static {
    Arrays.sort( new String[0], (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1, o2));
  }
}
