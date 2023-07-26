fun foo(f: (String?) -> Int) {}

fun test() {
    foo {
        if (it == null) return@foo 1
        if (it == "a") 2<caret> else 0
    }
}
//HIGHLIGHTED: return@foo 1
//HIGHLIGHTED: foo
//HIGHLIGHTED: 2
//HIGHLIGHTED: 0