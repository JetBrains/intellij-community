// PROBLEM: none
// IGNORE_K1

fun main() {
    println(Bar(foo = "initial foo").property)
}

class Bar(private <caret>val foo: String) {
    val property = foo.myDsl {
        "DSL-local foo: $foo"
    }
}

class MyBuilder(foo: String) {
    val foo = "builder-local foo: $foo"
}

fun String.myDsl(init: MyBuilder.() -> String) =
    MyBuilder(this).run(init)