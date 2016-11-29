import java.util.*;

public class MyTest {
  static {
    Arrays.sort( new String[0], (t, t1) -> String.CASE_INSENSITIVE_ORDER.compare(t, t1));
  }
}
