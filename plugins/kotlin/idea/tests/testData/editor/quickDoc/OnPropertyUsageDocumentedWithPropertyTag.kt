/**
 * @property bar bar property description should be taken from this tag
 */
class Foo {
    val bar: String = "bar"
}

fun usage() {
    Foo().b<caret>ar
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">bar</span><span style="">: </span><span style="color:#000000;">String</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>bar property description should be taken from this tag</p></div><table class='sections'></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/classKotlin.svg"/>&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a><br/></div>
