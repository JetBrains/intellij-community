import java.util.*;

class Test {

  void foo(String s) {
    List<String> tmp;
    List<String> list = switch (s) {
      case "singleton" -> {
          List<String> model = new ArrayList<>();
          model.add("foo");
          break tmp = process(model);
      }
      default -> new ArrayList<>();
    };
  }

  List<String> process(List<String> model) {
    return model;
  }
}