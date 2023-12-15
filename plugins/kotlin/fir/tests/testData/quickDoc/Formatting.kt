/**
 * *foo* **bar** ~~baz~~
 *
 * > quote line
 * > another quote line
 */
fun <caret>test() {}

//INFO: <div class='definition'><pre>fun test(): Unit</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'><em>foo</em> <strong>bar</strong> <del>baz</del></p>
//INFO:   <blockquote><p>quote line  another quote line</p>
//INFO: </blockquote></div><table class='sections'></table><div class='bottom'><icon src="file"/>&nbsp;Formatting.kt<br/></div>
