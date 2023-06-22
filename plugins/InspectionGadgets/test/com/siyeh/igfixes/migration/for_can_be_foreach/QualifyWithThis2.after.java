import java.util.*;

class QualifyWithThis2 {
  List<String> values = new ArrayList<>();
  void foo() {
    int size = values.size();
    List<String> values = new ArrayList<>();  // Let's hide field "values" with local variable

      for (String value : this.values) {
      }
  }
}