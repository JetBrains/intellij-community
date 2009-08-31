class X{
  def foo() {
    <error descr="'java.lang.String' is not an enclosing class">String.super</error>.toString()
  }
}