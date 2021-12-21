open class C() {
    /**
     * This property returns zero.
     */
    open val foo: Int get() = 0
}

class D(): C() {
    override val foo: Int get() = 1
}


fun test() {
    D().f<caret>oo
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">foo</span><span style="">: </span><span style="color:#000000;">Int</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>This property returns zero.</p></div><table class='sections'></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/classKotlin.svg"/>&nbsp;<a href="psi_element://D"><code><span style="color:#000000;">D</span></code></a><br/></div>
