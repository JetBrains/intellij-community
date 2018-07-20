// "Replace with 'List.subList().clear()'" "false"
import java.util.List;

class Test {
  void removeRange(List<String> list, int from, int to) {
    f<caret>or (int i = from; i < to; i++) {
      list.remove(i); // another inspection will fire here: Suspicious List.remove in the loop
    }
  }
}