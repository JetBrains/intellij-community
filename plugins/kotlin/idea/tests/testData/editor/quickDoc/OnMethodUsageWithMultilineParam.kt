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
//INFO:     <span style="color:#000000;">test</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation on two lines.</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://test"><code><span style="color:#0000ff;">test</span></code></a></code> - String on two lines</td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnMethodUsageWithMultilineParam.kt<br/></div>
