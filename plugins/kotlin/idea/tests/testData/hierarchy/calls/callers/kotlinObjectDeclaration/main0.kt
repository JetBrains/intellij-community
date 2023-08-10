object <caret>Foo {
    fun bar(x: Int) = x + 1
    fun baz(x: Int, y: Int) = x * bar(y)
}

val Z = Foo.bar(1)

fun quux() {
    val a = Foo.bar(Z)
    val b = Foo.baz(a, Foo.bar(a))
    println(Foo.baz(a, b))
}
