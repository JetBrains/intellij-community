// IS_APPLICABLE: true
// WITH_RUNTIME
// AFTER-WARNING: Parameter 'p' is never used

fun foo(p: List<String> = listOf<caret><String>()) {
}
