// PROBLEM: none
class Foo

fun id(): Foo = Foo()

fun test(expr: Foo) {
    id().someExtension.length
}

val <caret>Foo.someExtension: String
    get() = "extension"