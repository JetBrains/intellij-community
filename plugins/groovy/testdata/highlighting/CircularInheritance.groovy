class <error descr="Method 'invokeMethod' is not implemented"><error descr="Cyclic inheritance involving 'Foo'">Foo</error></error> extends Bar {}
class <error descr="Method 'invokeMethod' is not implemented"><error descr="Cyclic inheritance involving 'Bar'">Bar</error></error> extends Foo {}

println(new Foo())