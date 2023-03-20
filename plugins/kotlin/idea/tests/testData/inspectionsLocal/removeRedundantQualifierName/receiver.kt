package my.simple.name

class Foo {
    fun foo() {}
}

val bar = Foo()

fun a() {
    my.simple.name<caret>.bar.foo()
}