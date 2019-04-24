import java.util.*;

class Test {

  void foo(String s) {
    List<String> list = switch (s) {
      case "singleton" -> {
          List<String> model = new ArrayList<>();
          model.add("foo");
          break process(model);
      }
      default -> new ArrayList<>();
    };
  }

  List<String> process(List<String> model) {
    return model;
  }
}