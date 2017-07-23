<error descr="Cyclic inheritance involving 'Foo'">class Foo extends Bar</error> {}
<error descr="Cyclic inheritance involving 'Bar'">class Bar extends Foo</error> {}

println(new Foo())