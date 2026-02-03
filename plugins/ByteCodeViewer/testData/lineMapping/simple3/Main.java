package simple3;

public class Main {
  String method1(boolean value) {
    if (value == true) {
      return "baz";
    }
    return "baz";
  }

  String method2(boolean value) {
    if (value == Boolean.TRUE) {
      return "bar";
    }
    return "baz";
  }

  String method3(boolean value) {
    if (value == Boolean.FALSE) {
      return "bar";
    }
    return "baz";
  }

  String method(boolean value) {
    if (Boolean.TRUE.equals(returnsBool(value))) {
      return "foo";
    }
    return "baz";
  }

  public Boolean returnsBool(boolean value) {
    return Math.random() > 0.5;
  }
}
