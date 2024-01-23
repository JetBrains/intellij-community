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

//INFO: <div class='definition'><pre>fun testMethod(test: String): Unit</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation on two lines.</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://test"><code><span style="color:#0000ff;">test</span></code></a></code> - String on two lines</td></table><div class='bottom'><icon src="file"/>&nbsp;OnMethodUsageWithMultilineParam.kt<br/></div>
