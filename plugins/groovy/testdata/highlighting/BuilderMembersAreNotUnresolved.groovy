class MyBuilder {
  @Override
  Object getProperty(String property) {
    return super.getProperty(property)
  }

  @Override
  Object invokeMethod(String name, Object args) {
    return super.invokeMethod(name, args)
  }

  @Override
  void setProperty(String property, Object newValue) {
    super.setProperty(property, newValue)
  }
}

def b = new MyBuilder()
println b.foo
println new Object().<warning descr="Can not resolve symbol 'foo'">foo</warning>
b.foo = 2
b.bar()