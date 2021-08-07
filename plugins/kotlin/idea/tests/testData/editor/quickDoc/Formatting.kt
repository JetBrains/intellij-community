/**
 * *foo* **bar** ~~baz~~
 *
 * > quote line
 * > another quote line
 */
fun <caret>test() {}

//INFO: <div class='definition'><pre><font color="808080"><i>Formatting.kt</i></font><br>public fun <b>test</b>(): Unit</pre></div><div class='content'><p><em>foo</em> <strong>bar</strong> <del>baz</del></p>
//INFO:   <blockquote><p>quote line  another quote line</p>
//INFO: </blockquote></div><table class='sections'></table>
