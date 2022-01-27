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

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testMethod</span>()<span style="">: </span><span style="color:#000000;">Unit</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some documentation.</p>
//INFO: <pre><code style='font-size:96%;'>
//INFO: <span style=""><span style="">Code&#32;block</span></span>
//INFO: <span style="">&#32;&#32;&#32;&#32;<span style="">Second&#32;line</span></span>
//INFO:
//INFO: <span style=""><span style="">Third&#32;line</span></span>
//INFO: </code></pre><p>Text between code blocks.</p>
//INFO: <pre><code style='font-size:96%;'>
//INFO: </code></pre><p>Text after code block.</p></div><table class='sections'></table>
