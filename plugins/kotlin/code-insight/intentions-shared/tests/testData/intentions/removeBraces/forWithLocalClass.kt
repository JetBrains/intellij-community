// IS_APPLICABLE: false
fun foo(a: List<Int>) {
    for (x in a) {<caret>
        class C
    }
}