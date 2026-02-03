class X{
  def foo() {
    <error descr="Qualified super is allowed only in nested/inner classes">String.super</error>.toString()
  }
}