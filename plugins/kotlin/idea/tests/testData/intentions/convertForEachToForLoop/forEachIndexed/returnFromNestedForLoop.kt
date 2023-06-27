// WITH_STDLIB
fun test(itr: Iterable<String>) {
    itr.forEachIndexed<caret> { _, e ->
        for (c in e) {
            if (c.code < 10) continue
            if (c.code < 32) return@forEachIndexed
        }
    }
}