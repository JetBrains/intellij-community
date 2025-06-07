fun f(s : String?) : Boolean {
    return <caret>foo@(s?.equals("a"))!!
}