fun context() {
    fun local() {

    }

    <caret>local()
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">local</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">local</span>()<span style="">: </span><span style="color:#000000;">Unit</span></pre></div>
