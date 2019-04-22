import java.util.*;

public class Test {

  void foo() {
    process(Collections.singletonList<caret>("foo"));
  }

  void process(List<String> model) {
  }
}