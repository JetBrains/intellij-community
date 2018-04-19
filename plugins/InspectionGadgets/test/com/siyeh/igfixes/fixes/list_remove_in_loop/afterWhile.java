// "Replace with 'List.subList().clear()'" "INFORMATION"
import java.util.List;

class Test {
  void removeTail(List<String> list, int max) {
      if (list.size() > max) {
          list.subList(max, list.size()).clear();
      }
  }
}