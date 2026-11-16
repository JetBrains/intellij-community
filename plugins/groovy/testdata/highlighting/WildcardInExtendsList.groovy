interface Parent<T> {}

class P<T> {}

class Child extends P<<error descr="Wildcards not allowed in 'extends' list">? extends String</error>> implements Parent<<error descr="Wildcards not allowed in 'implements' list">? extends String</error>> {

}
interface I<T> {}
interface J extends I<<error descr="Wildcards not allowed in 'extends' list">?</error>> {}

print "foo"
