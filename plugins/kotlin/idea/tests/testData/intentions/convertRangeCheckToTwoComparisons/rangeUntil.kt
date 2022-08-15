// WITH_STDLIB
// LANGUAGE_VERSION: 1.8

@OptIn(kotlin.ExperimentalStdlibApi::class)
fun foo(bar: Int) {
    bar in 1..<10<caret>
}