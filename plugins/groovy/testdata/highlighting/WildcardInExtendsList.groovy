interface Parent<T> {}

class P<T> {}

class Child extends P<<error descr="A super type may not specify a wildcard type">? extends String</error>> implements Parent<<error descr="A super type may not specify a wildcard type">? extends String</error>> {

}

print "foo"
