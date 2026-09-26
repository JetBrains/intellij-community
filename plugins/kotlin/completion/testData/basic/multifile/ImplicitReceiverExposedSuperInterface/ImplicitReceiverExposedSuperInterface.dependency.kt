private interface FooProvider {
    fun foo() { println("foo") }
}

class FooScope : FooProvider

fun fooBar(scope: FooScope.() -> Unit) {
    FooScope().scope()
}