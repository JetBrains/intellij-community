class A {
  class Inner {
    Inner(String a) {}
  }

  def foo() {
    new Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A)'">()</warning> // incorrect
    new Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A, A)'">(new A())</warning> // incorrect
    new Inner("") // correct
    new Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A, A, java.lang.String)'">(new A(), "")</warning> // incorrect
  }

  static def bar() {
    new <error descr="Cannot reference non-static symbol 'A.Inner' from static context">Inner</error><warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '()'">()</warning> // incorrect
    new Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A)'">(new A())</warning> // // incorrect
    new Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(java.lang.String)'">("")</warning> // incorrect
    new Inner(new A(), "") // correct
  }
}

new A.Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '()'">()</warning> // incorrect
new A.Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(A)'">(new A())</warning> // // incorrect
new A.Inner<warning descr="Constructor 'Inner' in 'A.Inner' cannot be applied to '(java.lang.String)'">("")</warning> // incorrect
new A.Inner(new A(), "") // correct
