/**
Some documentation

 * @param a Some int
 * @param b String
 * @return Return [a] and nothing else
 */
fun testMethod(a: Int, b: String) {

}

fun test() {
    <caret>testMethod(1, "value")
}

//INFO: <div class='definition'><pre>fun testMethod(a: Int, b: String): Unit</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://a"><code style='font-size:96%;'><span style="color:#4585be;">a</span></code></a></code> - Some int<p><code><a href="psi_element://b"><code style='font-size:96%;'><span style="color:#4585be;">b</span></code></a></code> - String</td><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'>Return <span style="border:1px solid;border-color:#ff0000;">a</span> and nothing else</td></table><div class='bottom'><icon src="file"/>&nbsp;OnMethodUsageWithReturnAndLink.kt<br/></div>
