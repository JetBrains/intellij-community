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

//INFO: <div class='definition'><pre>override fun foo(): Int</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>This method returns zero.</p></div><table class='sections'></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://D"><code><span style="color:#000000;">D</span></code></a><br/></div>
