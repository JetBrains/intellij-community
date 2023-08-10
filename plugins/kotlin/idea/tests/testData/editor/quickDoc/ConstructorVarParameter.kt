class C(var v: Int) {
    fun foo() {
        print(<caret>v)
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">var</span> <span style="color:#660e7a;font-weight:bold;">v</span><span style="">: </span><span style="color:#000000;">Int</span></pre></div>
