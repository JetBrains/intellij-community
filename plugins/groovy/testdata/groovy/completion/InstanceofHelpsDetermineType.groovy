public class Parent {

  def foo(o) {
    if (o instanceof String) {
      o.substr<caret>
    }
  }

}