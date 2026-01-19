fun String.foo() {
    bar(::<caret>)
}

fun bar(p: () -> Unit) { }
fun bar(p: String.() -> Unit) { }


// EXIST: topLevelFun
// ABSENT: extFun

// IGNORE_K2
