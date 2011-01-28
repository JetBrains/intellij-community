class Foo {
}

print <warning descr="Type 'Foo' doesnt implement Comparable"><warning descr="Type 'Foo' cannot be iterated in range because it does not have method 'previous()'"><warning descr="Type 'Foo' cannot be iterated in range because it does not have method 'next()'">new Foo()..new Foo()</warning></warning></warning>