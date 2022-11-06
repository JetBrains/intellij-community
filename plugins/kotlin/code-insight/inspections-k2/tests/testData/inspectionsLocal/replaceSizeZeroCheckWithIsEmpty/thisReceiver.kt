// WITH_STDLIB

fun test() {
    doubleArrayOf(1.0, 2.5).run {
        0 =<caret>= size
    }
}