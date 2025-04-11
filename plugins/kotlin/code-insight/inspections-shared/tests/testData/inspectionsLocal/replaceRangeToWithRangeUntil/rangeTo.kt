// WITH_STDLIB
// LANGUAGE_VERSION: 1.8

@OptIn(ExperimentalStdlibApi::class)
fun foo(a: Int) {
    for (i in <caret>0.rangeTo(a - 1)) {

    }
}