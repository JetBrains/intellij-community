// FIX: Replace with 'firstOrNull()'
// WITH_STDLIB
// SKIP_ERRORS_BEFORE
// SKIP_ERRORS_AFTER

fun test(flag: Boolean, list: List<String>): String? {
    return when (flag) {
        true -> <caret>if (list.size > 0) {
            list[0]
        } else {
            null
        }
        false -> null
    }
}
