// "Replace 'if else' with '?:'" "INFORMATION"
import java.util.*;

class Test {
  public void multiAssignment(boolean b) {
          List<Integer> l;
          i<caret>f (b) {
              l = new ArrayList<>(1);
          } else l = new ArrayList<>(3);
      }
}
