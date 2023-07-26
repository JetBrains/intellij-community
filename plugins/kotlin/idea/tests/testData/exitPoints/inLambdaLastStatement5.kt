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
//HIGHLIGHTED: return@foo 1
//HIGHLIGHTED: foo
//HIGHLIGHTED: 2
//HIGHLIGHTED: y()
//HIGHLIGHTED: 0