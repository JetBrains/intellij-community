class Base {

}

class Derived1 extends Base {

}

class Derived2 extends Base {

}

vo<caret>id foo(Base a, Integer b) {

}

foo(new Derived1(), 1)

foo(new Derived2(), 2)