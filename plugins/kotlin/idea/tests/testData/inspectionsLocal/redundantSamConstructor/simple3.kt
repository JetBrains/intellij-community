fun usage(r: Runnable) {}

fun test() {
    usage(Runnable<caret> { })
}
