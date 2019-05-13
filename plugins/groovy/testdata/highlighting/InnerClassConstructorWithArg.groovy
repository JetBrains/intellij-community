class A {
  class Inner {
    Inner(A a) {}
  }

  def foo() {
    new Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A)'">()</warning> // incorrect
    new Inner(new A()) // correct
    new Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A, A, A)'">(new A(), new A())</warning> // incorrect
  }

  static def bar() {
    new <error descr="Cannot reference non-static symbol 'A.Inner' from static context">Inner</error><warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '()'">()</warning> // no enclosing instance & incorrect
    new Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A)'">(new A())</warning> // incorrect
    new Inner(new A(), new A()) //correct
  }
}

new A.Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '()'">()</warning> // incorrect
new A.Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A)'">(new A())</warning> // incorrect
new A.Inner(new A(), new A()) // correct
