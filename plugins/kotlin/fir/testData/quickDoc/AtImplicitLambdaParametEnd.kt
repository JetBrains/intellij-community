fun foo() {
    listOf(1).forEach {
        println(it<caret>)
    }
}

//INFO: <div class='definition'><pre>it: Int</pre></div>
