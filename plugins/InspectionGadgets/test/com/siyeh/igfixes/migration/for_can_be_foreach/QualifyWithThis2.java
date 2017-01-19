import java.util.*;

class QualifyWithThis2 {
  List<String> values = new ArrayList<>();
  void foo() {
    int size = values.size();
    List<String> values = new ArrayList<>();  // Let's hide filed "values" with local variable

    <caret>for (int i = 0; i < size; i++) {
      String value = this.values.get(i);
    }
  }
}