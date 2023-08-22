/**
Some documentation

 * @param[a] Some int
 * @param[b] String
 */
fun testMethod(a: Int, b: String) {

}

fun test() {
    <caret>testMethod(1, "value")
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testMethod</span>(
//INFO:     <span style="color:#000000;">a</span><span style="">: </span><span style="color:#000000;">Int</span>,
//INFO:     <span style="color:#000000;">b</span><span style="">: </span><span style="color:#000000;">String</span>
//INFO: )<span style="">: </span><span style="color:#000000;">Unit</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://a"><code style='font-size:96%;'><span style="color:#0000ff;">a</span></code></a></code> - Some int<p><code><a href="psi_element://b"><code style='font-size:96%;'><span style="color:#0000ff;">b</span></code></a></code> - String</td></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/kotlin_file.svg"/>&nbsp;OnMethodUsageWithBracketsInParam.kt<br/></div>
