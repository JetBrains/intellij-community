
interface Foo

fun foo(a: Any) {}

fun Foo.bar() {
    foo(th<caret>is)
}

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;"><a href="psi_element://Foo">Foo</a></span><span style="">.</span><span style="color:#000000;">bar</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;ExtensionReceiver.kt<br/></div>

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;"><a href="psi_element://Foo">Foo</a></span><span style="">.</span><span style="color:#000000;">bar</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div></pre></div><table class='sections'><p></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;ExtensionReceiver.kt<br/></div>
