// PROBLEM: none
class Foo {
    fun id(): Foo = this
}

fun test(expr: Foo) {
    expr.id().someExtension().length
}

fun <caret>Foo.someExtension(): String = "extension"
