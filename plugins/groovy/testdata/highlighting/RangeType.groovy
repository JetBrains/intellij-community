class Foo {
}

print <warning descr="Type 'Foo' cannot be iterated in range because it does not have method 'next()'"><warning descr="Type 'Foo' cannot be iterated in range because it does not have method 'previous()'"><warning descr="Type 'Foo' doesn't implement Comparable">new Foo()..new Foo()</warning></warning></warning>