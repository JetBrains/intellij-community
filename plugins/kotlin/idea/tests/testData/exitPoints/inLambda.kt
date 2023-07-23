fun foo(f: (String?) -> Int) {}

fun test() {
    foo {
        if (it == null) return@<caret>foo 1
        (1+1)
        if (it == "a") 2 else 0
    }
}
//HIGHLIGHTED: return@foo 1
//HIGHLIGHTED: foo
//HIGHLIGHTED: 2
//HIGHLIGHTED: 0