// PROBLEM: none
// IGNORE_K1
class Test(<caret>val foo: String) {
    init {
        val property = foo.myDsl {
            "DSL-local foo: $foo"
        }
    }
}

class MyBuilder(foo: String) {
    val foo = "builder-local foo: $foo"
}

fun String.myDsl(init: MyBuilder.() -> String) =
    MyBuilder(this).run(init)