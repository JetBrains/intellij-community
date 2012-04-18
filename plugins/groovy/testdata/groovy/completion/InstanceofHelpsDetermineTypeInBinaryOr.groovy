public class Parent {

  def foo(o) {
    return !(o instanceof String) || o.subst<caret>
  }

}