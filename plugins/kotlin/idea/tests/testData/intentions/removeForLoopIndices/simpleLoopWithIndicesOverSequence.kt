// WITH_STDLIB
fun foo(bar: Sequence<String>) {
    for ((i<caret>,a) in bar.withIndex()) {

    }
}