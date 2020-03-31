// "Replace with 'new String[0][0]'" "true"
import java.util.List;

class Test {
  void test(List<String[]> list) {
    list.toArray(new <caret>Integer[10]);
  }
}