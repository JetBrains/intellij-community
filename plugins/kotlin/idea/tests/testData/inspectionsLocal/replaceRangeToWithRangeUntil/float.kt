// WITH_STDLIB
// LANGUAGE_VERSION: 1.8

@OptIn(ExperimentalStdlibApi::class)
fun foo(a: Float) {
    1f<caret>..a - 1
}