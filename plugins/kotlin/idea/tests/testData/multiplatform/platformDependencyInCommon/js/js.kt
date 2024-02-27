fun checkCloneableIsAbsent(array: Array<String>) {
    array.<!UNRESOLVED_REFERENCE!>clone<!>()
}

fun checkSynchronizedIsDeprecatedInJs() {
    <!DEPRECATION_ERROR!>synchronized<!>(Any()) {
    }
}
