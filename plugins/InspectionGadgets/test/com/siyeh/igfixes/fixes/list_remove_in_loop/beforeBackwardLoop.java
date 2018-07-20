// "Replace with 'List.subList().clear()'" "GENERIC_ERROR_OR_WARNING"
import java.util.List;

class Test {
  void removeRange(List<String> list, int from, int to) {
    fo<caret>r (int i = to - 1; i >= from; i--) {
      list.remove(i);
    }
  }
}