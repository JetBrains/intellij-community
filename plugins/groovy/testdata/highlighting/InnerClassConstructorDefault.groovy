class A {
  class Inner {
    def foo() {
      new Inner() // correct
      new Inner<warning descr="Cannot apply default constructor for class 'Inner'">(new A())</warning> // incorrect
    }
  }

  def foo() {
    new Inner() //correct
    new Inner<warning descr="Cannot apply default constructor for class 'Inner'">(new A())</warning> // incorrect
  }

  static def bar() {
    new <error descr="Cannot reference non-static symbol 'A.Inner' from static context">Inner</error>() // no enclosing instance
    new Inner(new A()) // correct
  }
}

new A.Inner() // correct, implicit null
new A.Inner(new A()) // correct
new A.Inner<warning descr="Cannot apply default constructor for class 'Inner'">(new A(), new A())</warning> // correct
