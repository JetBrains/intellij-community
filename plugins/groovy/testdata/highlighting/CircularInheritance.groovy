<error descr="Method 'invokeMethod' is not implemented"><error descr="Cyclic inheritance involving 'Foo'">class Foo extends Bar</error></error> {}
<error descr="Method 'invokeMethod' is not implemented"><error descr="Cyclic inheritance involving 'Bar'">class Bar extends Foo</error></error> {}

println(new Foo())