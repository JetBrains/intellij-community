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

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testMethod</span>()<span style="">: </span><span style="color:#000000;">Unit</span></pre></div><div class='content'><p>Some documentation.</p>
//INFO: <pre><code>
//INFO: <span style="">Code&#32;block</span>
//INFO: &#32;&#32;&#32;&#32;<span style="">Second&#32;line</span>
//INFO:
//INFO: <span style="">Third&#32;line</span>
//INFO: </code></pre><p>Text between code blocks.</p>
//INFO: <pre><code>
//INFO: </code></pre><p>Text after code block.</p></div><table class='sections'></table>
