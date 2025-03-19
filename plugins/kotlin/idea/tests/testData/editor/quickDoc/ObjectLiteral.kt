interface I

fun m() {
    obj<caret>ect : I {}
}

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">object</span> : <span style="color:#000000;"><a href="psi_element://I">I</a></span></pre></div>
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">local</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">`&lt;no name provided&gt;`</span>
//INFO:     <span style="">: </span><span style="color:#000000;"><a href="psi_element://I">I</a></span></pre></div>

