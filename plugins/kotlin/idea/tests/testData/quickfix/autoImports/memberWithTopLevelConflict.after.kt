import p1.sleep

// "Import" "true"
// WITH_STDLIB
// FULL_JDK
// ERROR: Unresolved reference: sleep


fun usage() {
    sleep<caret>()
}