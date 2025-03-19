/**
Some documentation

 * @receiver Some int
 * @param b String
 * @return Return [a] and nothing else
 */
fun Int.testMethod(b: String) {

}

fun test() {
    1.<caret>testMethod("value")
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span><span style="">.</span><span style="color:#000000;">testMethod</span>(
//INFO:     <span style="color:#000000;">b</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation</p></div><table class='sections'><tr><td valign='top' class='section'><p>Receiver:</td><td valign='top'>Some int</td><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://b"><code><span style="color:#0000ff;">b</span></code></a></code> - String</td><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'>Return <span style="border:1px solid;border-color:#ff0000;">a</span> and nothing else</td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnMethodUsageWithReceiver.kt<br/></div>
