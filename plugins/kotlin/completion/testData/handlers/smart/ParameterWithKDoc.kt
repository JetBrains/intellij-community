class Apple
fun makeJuice(a: Apple) {}
fun makeApple(): Apple { return Apple() }

fun test() {
    makeJuice( <caret> // wow
    /**
     * documentation
     */
    )
}

// ELEMENT: makeApple
// TAIL_TEXT: () (<root>)
