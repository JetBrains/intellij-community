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

//INFO: <div class='definition'><pre>override val foo: Int</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>This property returns zero.</p></div><table class='sections'></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://D"><code><span style="color:#000000;">D</span></code></a><br/></div>
