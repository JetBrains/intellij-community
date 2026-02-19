// WITH_STDLIB
// FIX: Change call to 'map'

fun foo(c: Sequence<String>, f: Boolean) {
    c.<caret>mapNotNull {
        if (f) {
            return@mapNotNull
        }
    }
}