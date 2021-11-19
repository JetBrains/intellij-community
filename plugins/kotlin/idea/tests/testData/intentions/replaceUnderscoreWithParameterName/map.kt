// WITH_STDLIB
// AFTER-WARNING: Variable 'key' is never used

fun foo(map: Map<String, Int>) {
    for ((<caret>_, _) in map) {

    }
}