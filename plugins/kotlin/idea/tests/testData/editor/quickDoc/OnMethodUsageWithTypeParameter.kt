/**
Some documentation

 * @param T the type parameter
 * @param a Some int
 * @param b String
 */
fun <T> testMethod(a: Int, b: String) {

}

fun test() {
    <caret>testMethod(1, "value")
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="">&lt;</span><span style="color:#20999d;">T</span><span style="">&gt;</span> <span style="color:#000000;">testMethod</span>(
//INFO:     <span style="color:#000000;">a</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span>,
//INFO:     <span style="color:#000000;">b</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://T"><code><span style="color:#20999d;">T</span></code></a></code> - the type parameter<p><code><a href="psi_element://a"><code><span style="color:#0000ff;">a</span></code></a></code> - Some int<p><code><a href="psi_element://b"><code><span style="color:#0000ff;">b</span></code></a></code> - String</td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnMethodUsageWithTypeParameter.kt<br/></div>
