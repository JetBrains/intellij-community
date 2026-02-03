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
    fun <caret>returnE() = "a"

    /**
     * @return A.
     * @return B.
     */
    fun retrunF() {}
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">returnE</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span></pre></div><table class='sections'><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><p style='margin-top:0;padding-top:0;'>one two</p>
//INFO: <p>four</p></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://ReturnContainerC"><code><span style="color:#000000;">ReturnContainerC</span></code></a><br/></div>
