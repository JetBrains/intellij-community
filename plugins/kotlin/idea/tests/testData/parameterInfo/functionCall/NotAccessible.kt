class C {
    fun foo(){}
    protected fun foo(p: Int){}
}

fun f(c: C) {
    c.foo(<caret>1)
}
// TODO: wrong name resolution. see: change
/*
Text: (<no parameters>), Disabled: true, Strikeout: false, Green: false
Text_K2: (<no parameters>), Disabled: true, Strikeout: false, Green: true
*/