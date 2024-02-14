// WITH_STDLIB
// FIX: Change call to 'map'

fun foo(c: Sequence<String>) {
    c.<caret>mapNotNull {
        return@mapNotNull ""
    }
}