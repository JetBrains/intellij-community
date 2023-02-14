// WITH_STDLIB
// HIGHLIGHT: INFORMATION

fun test() {
    runCatching {
        /* lots of code*/
    }.let<caret> { println(it) }
}