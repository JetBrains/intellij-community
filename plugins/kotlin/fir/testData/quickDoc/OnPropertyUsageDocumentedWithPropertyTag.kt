/**
 * @property bar bar property description should be taken from this tag
 */
class Foo {
    val bar: String = "bar"
}

fun usage() {
    Foo().b<caret>ar
}

//INFO: <div class='definition'><pre>val bar: String</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>bar property description should be taken from this tag</p></div><table class='sections'></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a><br/></div>
