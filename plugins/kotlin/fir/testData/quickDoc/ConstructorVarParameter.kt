class C(var v: Int) {
    fun foo() {
        print(<caret>v)
    }
}

//INFO: <div class='definition'><pre>v: Int</pre></div>
