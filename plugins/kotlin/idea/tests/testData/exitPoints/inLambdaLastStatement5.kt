fun foo(f: (String?) -> Int) {}

fun y(): Int = 11
fun test() {
    foo {
        if (it != null) return@foo 1
        if (it == "a") {
            if (it == "aa") y<caret>() else 2
        } else {
            0
        }
    }
}
// no exit point highlighting as to KTIJ-26395: we should not highlight exit points on the latest statement as it interferes with variable/call/type highlighting
//HIGHLIGHTED: y
//HIGHLIGHTED: y