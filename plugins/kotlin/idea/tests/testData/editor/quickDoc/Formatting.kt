/**
 * *foo* **bar** ~~baz~~
 *
 * > quote line
 * > another quote line
 */
fun <caret>test() {}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">test</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'><em>foo</em> <strong>bar</strong> <del>baz</del></p>
//INFO:   <blockquote><p>quote line  another quote line</p>
//INFO: </blockquote></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;Formatting.kt<br/></div>
