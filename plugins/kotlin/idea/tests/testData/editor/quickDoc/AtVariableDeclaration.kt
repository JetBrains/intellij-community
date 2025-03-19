fun some() : String? = null

fun test() {
    val <caret>test = some()
}


//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">val</span> <span style="color:#000000;">test</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span><span style="">?</span></pre></div>
//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">local</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#000000;">test</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span><span style="">?</span></pre></div>