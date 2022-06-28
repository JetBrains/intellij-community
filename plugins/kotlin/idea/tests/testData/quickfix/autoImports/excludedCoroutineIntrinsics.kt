// "Import" "false"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.2
// ACTION: Create function 'suspendCoroutineOrReturn'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: suspendCoroutineOrReturn

fun some() {
    suspendCoroutineOrReturn<caret> {}
}
