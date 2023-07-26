fun foo(f: (String?) -> Int) {}

fun test() {
    foo {
        if (it != null) return@foo 1
        0<caret>
    }
}
//HIGHLIGHTED: return@foo 1
//HIGHLIGHTED: foo
//HIGHLIGHTED: 0