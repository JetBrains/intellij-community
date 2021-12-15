/**
 * Some documentation
 * on two lines.
 *
 * @param test String
 * on two lines
 */
fun testMethod(test: String) {
}

fun test() {
    <caret>testMethod("")
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testMethod</span>(
//INFO:     <span style="color:#000000;">test</span><span style="">: </span><span style="color:#000000;">String</span>
//INFO: )<span style="">: </span><span style="color:#000000;">Unit</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation on two lines.</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://test"><code style='font-size:96%;'><span style="color:#0000ff;">test</span></code></a></code> - String on two lines</td></table>
