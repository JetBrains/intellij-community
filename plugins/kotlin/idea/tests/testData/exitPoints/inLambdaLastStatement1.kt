fun foo(f: (String?) -> Int) {}

fun test() {
    foo {
        if (it != null) return@foo 1
        0~
    }
}
// no exit point highlighting as to KTIJ-26395: we should not highlight exit points on the latest statement as it interferes with variable/call/type highlighting
