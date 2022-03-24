// WITH_STDLIB
// FIX: Change call to 'map'

fun foo(c: Collection<String>, f: Boolean) {
    c.<caret>mapNotNull {
        if (f) {
            return@mapNotNull
        }
    }
}