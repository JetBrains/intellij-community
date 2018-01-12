@interface SimpleAnnotation {}

@SimpleAnnotation
class SimpleClass {

  private int field = 1

  @java.lang.Deprecated
  def foo() { return field }

  String bar() { return "abc$field" }
}
