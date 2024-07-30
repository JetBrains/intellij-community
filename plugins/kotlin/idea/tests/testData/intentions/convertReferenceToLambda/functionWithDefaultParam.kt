// WITH_STDLIB
// AFTER-WARNING: Parameter 'p' is never used
// AFTER-WARNING: Parameter 's' is never used

private fun m(l: List<String>) {
    l.forEach(::foo<caret>)
}

private fun foo(
    s: String?,
    p: String? = null,
) {
}