open class C() {
    /**
     * This method returns zero.
     */
    open fun foo(): Int = 0
}

class D(): C() {
    override fun foo(): Int = 1
}


fun test() {
    D().f<caret>oo()
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">foo</span>()<span style="">: </span><span style="color:#000000;">Int</span></pre></div><div class='content'><p>This method returns zero.</p></div><table class='sections'></table>
