import java.util.*;

class Test {
  public List<Integer> multiReturn(boolean b) {
          if <caret>(b) {
                  return new ArrayList<>(1);
          }
          else return new ArrayList<>(3);
  }
}
