// WITH_STDLIB
// LANGUAGE_VERSION: 1.8

@OptIn(ExperimentalStdlibApi::class)
fun foo(a: Int) {
    for (i in 0..<caret>a - 1) {

    }
}