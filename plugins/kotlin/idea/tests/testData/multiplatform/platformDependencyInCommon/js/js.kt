fun checkCloneableIsAbsent(array: Array<String>) {
    array.<!UNRESOLVED_REFERENCE!>clone<!>()
}

fun checkSynchronizedIsUnresolvedInJs() {
    <!UNRESOLVED_REFERENCE!>synchronized<!>(Any()) {
    }
}
