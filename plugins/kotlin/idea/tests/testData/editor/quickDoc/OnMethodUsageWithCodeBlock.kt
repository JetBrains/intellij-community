/**
 * Some documentation.
 *
 * ```
 * Code block
 *     Second line
 *
 * Third line
 * ```
 *
 * Text between code blocks.
 * ```
 * ```
 * Text after code block.
 */
fun testMethod() {

}

class C {
}

fun test() {
    <caret>testMethod(1, "value")
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testMethod</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation.</p>
//INFO: <pre><code><span style="">Code&#32;block<br></span><span style="">&#32;&#32;&#32;&#32;Second&#32;line<br></span><span style=""><br></span><span style="">Third&#32;line</span></code></pre><p>Text between code blocks.</p>
//INFO: <pre><code></code></pre><p>Text after code block.</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnMethodUsageWithCodeBlock.kt<br/></div>
