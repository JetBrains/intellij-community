import java.util.*;

class Test {

  void foo() {
      /*2*/
      /*3*/
      Map<String, String> model = new HashMap<>();
      model.put("foo", "bar");
      model.put("goo", "baz");
      process(/*1*/model/*4*/);
  }

  void process(Map<String, String> model) {

  }
}