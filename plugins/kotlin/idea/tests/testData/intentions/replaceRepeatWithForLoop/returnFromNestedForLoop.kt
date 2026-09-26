// WITH_STDLIB
fun foo() {
    <caret>repeat(5) {
        for (c in "abc") {
            if (c == 'a') continue
            if (c == 'b') return@repeat
        }
    }
}