// WITH_STDLIB
fun foo(times: List<Long>) {
    times.forEach <caret>{ Thread.sleep(it) }
}