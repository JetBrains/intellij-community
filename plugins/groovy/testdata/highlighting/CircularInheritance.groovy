<error descr="Cyclic inheritance involving 'Foo'"><error descr="Method 'invokeMethod' is not implemented">class Foo extends Bar</error></error> {}
<error descr="Cyclic inheritance involving 'Bar'"><error descr="Method 'invokeMethod' is not implemented">class Bar extends Foo</error></error> {}

println(new Foo())