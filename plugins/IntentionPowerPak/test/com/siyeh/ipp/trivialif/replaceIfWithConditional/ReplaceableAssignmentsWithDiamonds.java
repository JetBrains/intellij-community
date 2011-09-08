import java.util.*;

class Test {
  public void multiAssignment(boolean b) {
          List<Integer> l;
          if <caret>(b) {
              l = new ArrayList<>(1);
          } else l = new ArrayList<>(3);
      }
}
