// WITH_STDLIB
fun test(x: Any) {
    run {
        <caret>x as? String ?: return@run
    }
}