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
//INFO:     <span style="color:#000000;">first</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span>,
//INFO:     <span style="color:#000000;">second</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Test function</p></div><table class='sections'><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://first"><code><span style="color:#0000ff;">first</span></code></a></code> - Some<p><code><a href="psi_element://second"><code><span style="color:#0000ff;">second</span></code></a></code> - Other</td></table><div class='bottom'><icon src="AllIcons.Nodes.Package"/>&nbsp;<a href="psi_element://test"><code><span style="color:#000000;">test</span></code></a><br/><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnFunctionDeclarationWithPackage.kt<br/></div>
