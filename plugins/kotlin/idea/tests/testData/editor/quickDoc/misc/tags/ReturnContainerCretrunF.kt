class ReturnContainerC {
    /**
     * @return Something
     * better.
     */
    fun returnD() = 0

    /**
     * @return one
     * two
     *
     * four
     */
    fun returnE() = "a"

    /**
     * @return A.
     * @return B.
     */
    fun <caret>retrunF() {}
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">retrunF</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'>A.</td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://ReturnContainerC"><code><span style="color:#000000;">ReturnContainerC</span></code></a><br/></div>
