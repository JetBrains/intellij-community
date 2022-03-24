fun foo() {
    listOf(1).forEach {
        println(it<caret>)
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">value-parameter</span> <span style="color:#000000;">it</span><span style="">: </span><span style="color:#000000;">Int</span></pre></div>
