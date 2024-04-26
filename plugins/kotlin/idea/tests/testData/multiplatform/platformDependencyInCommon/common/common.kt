fun checkCloneableIsAbsent(array: Array<String>) {
    array.<!UNRESOLVED_REFERENCE!>clone<!>()
}

fun checkSynchronizedIsUnresolvedInCommon() {
    <!UNRESOLVED_REFERENCE!>synchronized<!>(Any()) {
    }
}
