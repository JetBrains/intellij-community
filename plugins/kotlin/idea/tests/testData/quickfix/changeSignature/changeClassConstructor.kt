// "Change the signature of constructor 'FooBar'" "true"

private class FooBar(val name: String)
fun test() {
    val foo = FooBar(1, <caret>"name")
}
