class UberInheritance {
    open class SeenSuperA {
        open fun superFun() {}
    }

    /**
     * [super]
     * @see [super]
     */
    class <caret>SeesSuperA : SeenSuperA() {
        override fun superFun() {
            super.superFun()
        }
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">SeesSuperA</span> : <span style="color:#000000;"><a href="psi_element://UberInheritance">UberInheritance</a></span><span style="">.</span><span style="color:#000000;"><a href="psi_element://UberInheritance.SeenSuperA">SeenSuperA</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'><span style="border:1px solid;border-color:#ff0000;">super</span></p></div><table class='sections'><tr><td valign='top' class='section'><p>See Also:</td><td valign='top'><a href="psi_element://super"><code><span style="color:#4585be;">super</span></code></a></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://UberInheritance"><code><span style="color:#000000;">UberInheritance</span></code></a><br/></div>
