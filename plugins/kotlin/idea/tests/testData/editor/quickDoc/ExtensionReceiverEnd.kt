
interface Foo

fun foo(a: Any) {}

fun Foo.bar() {
    foo(this<caret>)
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;"><a href="psi_element://Foo">Foo</a></span><span style="">.</span><span style="color:#000000;">bar</span>()<span style="">: </span><span style="color:#000000;">Unit</span></pre></div></pre></div><table class='sections'><p></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/kotlin_file.svg"/>&nbsp;ExtensionReceiverEnd.kt<br/></div>
