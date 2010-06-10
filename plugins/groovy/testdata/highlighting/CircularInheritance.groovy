class <error descr="Cyclic inheritance involving 'Foo'">Foo</error> extends Bar {}
class <error descr="Cyclic inheritance involving 'Bar'">Bar</error> extends Foo {}

println(new Foo())