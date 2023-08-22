// WITH_STDLIB
fun test(itr: Iterable<String>) {
    itr.forEach<caret> {
        for (c in it) {
            if (c.code < 10) continue
            if (c.code < 32) return@forEach
        }
    }
}