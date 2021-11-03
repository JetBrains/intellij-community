package test

/**

 *
 *
 * Test function

 *
 * @param first Some
 * @param second Other
 */
fun <caret>testFun(first: String, second: Int) = 12

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testFun</span>(
//INFO:     <span style="color:#000000;">first</span><span style="">: </span><span style="color:#000000;">String</span>,
//INFO:     <span style="color:#000000;">second</span><span style="">: </span><span style="color:#000000;">Int</span>
//INFO: )<span style="">: </span><span style="color:#000000;">Int</span></pre></div><div class='content'><p>Test function</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://first"><code style='font-size:96%;'><span style="color:#0000ff;">first</span></code></a></code> - Some<p><code><a href="psi_element://second"><code style='font-size:96%;'><span style="color:#0000ff;">second</span></code></a></code> - Other</td></table>
