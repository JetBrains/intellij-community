class Apple
fun makeJuice(a: Apple) {}
fun makeApple(): Apple { return Apple() }

fun test() {

    makeJuice(<caret> /* comment
    */)
}

// ELEMENT: makeApple
// TAIL_TEXT: () (<root>)