class MyDom {

  static void main(String[] args) {
    new Foo().foo({
      print ba<ref>r
    });
  }
}


class Foo {
  def bar = 2

  def foo(Closure c) {
    c.delegate = this
    c.call()
  }
}
