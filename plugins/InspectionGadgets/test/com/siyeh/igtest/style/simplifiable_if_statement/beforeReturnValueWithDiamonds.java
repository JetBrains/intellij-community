// "Replace 'if else' with '?:'" "INFORMATION"
import java.util.*;

class Test {
  public List<Integer> multiReturn(boolean b) {
          <caret>if (b) {
                  return new ArrayList<>(1);
          }
          else return new ArrayList<>(3);
  }
}
