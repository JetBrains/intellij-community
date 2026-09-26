class ParamReturnContainerA {
    /**
     * @param [p1] P1.
     * @param p2 P2.
     * Line 1.
     *
     * Line 3.
     * @return Return.
     */
    fun paramReturnA(p1: Int, p2: Int) = p1 + p2

    /**
     * @param p1 P1.
     * @return Return.
     * Line 1.
     *
     * Line 3.
     * @param [p2] P2.
     */
    fun <caret>paramReturnB(p1: Int, p2: Int) = p1 + p2

    /**
     * @return Return.
     * Line 1.
     *
     * Line 3.
     * @param [p1] P1.
     * @param p2 P2.
     */
    fun paramReturnC(p1: Int, p2: Int) = p1 + p2
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">paramReturnB</span>(
//INFO:     <span style="color:#000000;">p1</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span>,
//INFO:     <span style="color:#000000;">p2</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span></pre></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://p1"><code><span style="color:#0000ff;">p1</span></code></a></code> - P1.<p><code><a href="psi_element://p2"><code><span style="color:#0000ff;">p2</span></code></a></code> - P2.</td><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><p style='margin-top:0;padding-top:0;'>Return. Line 1.</p>
//INFO: <p>Line 3.</p></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://ParamReturnContainerA"><code><span style="color:#000000;">ParamReturnContainerA</span></code></a><br/></div>
