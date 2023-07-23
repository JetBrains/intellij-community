fun foo(f: (String?) -> Int) {}

fun test() {
    foo {
        if (it == null) return@<caret>foo 1
        0
    }
}
//HIGHLIGHTED: return@foo 1
//HIGHLIGHTED: foo
//HIGHLIGHTED: 0