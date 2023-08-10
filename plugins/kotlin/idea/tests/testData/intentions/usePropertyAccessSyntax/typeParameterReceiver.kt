// WITH_STDLIB

fun <T : Thread> foo(t: T) {
    t.<caret>setDaemon(true)
}

