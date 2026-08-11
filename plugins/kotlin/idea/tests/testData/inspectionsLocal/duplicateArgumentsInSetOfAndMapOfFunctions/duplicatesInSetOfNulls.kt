// WITH_STDLIB
// PROBLEM: Duplicate element in collection: 'null'
// HIGHLIGHT: GENERIC_ERROR_OR_WARNING
// FIX: none

fun a() {
    setOf(<caret>null, null, 1)
}