// "Replace with 'List.subList().clear()'" "GENERIC_ERROR_OR_WARNING"
import java.util.List;

class Test {
  void removeRange(List<String> list, int from, int to) {
    if(to <= from) return;

    f<caret>or (int i = from; i < to; i++) {
      list.remove(from);
    }
  }
}