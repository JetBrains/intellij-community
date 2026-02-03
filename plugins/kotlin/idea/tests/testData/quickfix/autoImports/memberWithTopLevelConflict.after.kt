import p1.sleep

// "Import function 'sleep'" "true"
// WITH_STDLIB
// FULL_JDK
// ERROR: Unresolved reference: sleep

fun usage() {
    sleep<caret>()
}
