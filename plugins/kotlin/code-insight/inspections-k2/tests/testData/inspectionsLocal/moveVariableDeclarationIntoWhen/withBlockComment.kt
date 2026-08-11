// HIGHLIGHT: INFORMATION
fun test(){
    /* aaa */
    val <caret>foo = 1 /* bbb */ /* ccc */
    when(foo) {
        1 -> {}
        2 -> {}
    }
}