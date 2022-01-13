fun some() : String? = null

fun test() {
    val <caret>test = some()
}


//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">val</span> <span style="color:#000000;">test</span><span style="">: </span><span style="color:#000000;">String</span><span style="">?</span></pre></div>
