val foo: Boolean = true

fun f() {
    if (foo) {
        // foo was true
        <caret>

        println(foo)
    }
}
