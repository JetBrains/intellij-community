public class Parent {

  def foo(o) {
    if (o instanceof String) {
    } else {
      o.substr<caret>
    }
  }

}