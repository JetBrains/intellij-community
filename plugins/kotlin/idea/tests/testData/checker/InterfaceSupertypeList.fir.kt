open class bar()

interface <error descr="[CONSTRUCTOR_IN_INTERFACE]">Foo()</error> : <error descr="[INTERFACE_WITH_SUPERCLASS]"><error descr="[SUPERTYPE_INITIALIZED_IN_INTERFACE]">bar</error></error>(), <error descr="[MANY_CLASSES_IN_SUPERTYPE_LIST]"><error descr="[SUPERTYPE_APPEARS_TWICE]">bar</error></error>, <error descr="[MANY_CLASSES_IN_SUPERTYPE_LIST]"><error descr="[SUPERTYPE_APPEARS_TWICE]">bar</error></error> {
}

interface Foo2 : <error descr="[INTERFACE_WITH_SUPERCLASS]">bar</error>, Foo {
}

open class Foo1() : bar(), <error descr="[MANY_CLASSES_IN_SUPERTYPE_LIST]"><error descr="[SUPERTYPE_APPEARS_TWICE]">bar</error></error>, Foo, <error descr="[SUPERTYPE_APPEARS_TWICE]"><error descr="[UNRESOLVED_REFERENCE]">Foo</error></error>() {}
open class Foo12 : bar(), <error descr="[MANY_CLASSES_IN_SUPERTYPE_LIST]"><error descr="[SUPERTYPE_APPEARS_TWICE]">bar</error></error> {}
