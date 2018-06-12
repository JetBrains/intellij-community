// "Replace 'if else' with '?:'" "INFORMATION"
import java.util.*;

class Test {
  public List<Integer> multiReturn(boolean b) {
      return b ? new ArrayList<Integer>(1) : new ArrayList<Integer>(3);
  }
}
