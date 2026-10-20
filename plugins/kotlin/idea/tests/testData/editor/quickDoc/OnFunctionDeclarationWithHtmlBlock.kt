/**
 * <p>
 * The boo fun does nothing special.
 * <p>
 * The function may be changed to return some value:
 * <blockquote><pre>
 *     fun boo() = 42
 * </pre></blockquote>
 * <p>
 */
fun <caret>boo(test: String) {}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">boo</span>(
//INFO:     <span style="color:#000000;">test</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>
//INFO: The boo fun does nothing special.
//INFO: <p>
//INFO: The function may be changed to return some value:
//INFO: <blockquote><pre>
//INFO:      fun boo() = 42
//INFO: </pre></blockquote>
//INFO: <p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnFunctionDeclarationWithHtmlBlock.kt<br/></div>
