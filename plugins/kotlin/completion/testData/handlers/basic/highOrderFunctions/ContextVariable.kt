fun String.xfoo(p: () -> Unit){}

fun X.test() {
    val local: () -> Unit = { }
    "a".xf<caret>
}

// IGNORE_K2
// ELEMENT: xfoo
// TAIL_TEXT: "(local) for String in <root>"
