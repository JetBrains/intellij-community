package foo

class Foo {

    val foo: Int = 42

    val bar: String = ""
}

operator fun Foo.component1(): Int = foo

operator fun Foo.component2(): String = bar