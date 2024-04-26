class Foo<caret>(val p: String)

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">constructor</span> <span style="color:#000000;">Foo</span>(
//K2_INFO:     <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">p</span><span style="">: </span><span style="color:#000000;">String</span>
//K2_INFO: )</pre></div><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/classKotlin.svg"/>&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a><br/></div>

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">constructor</span> <span style="color:#000000;">Foo</span>(
//INFO:     <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#000000;">p</span><span style="">: </span><span style="color:#000000;">String</span>
//INFO: )</pre></div></pre></div><table class='sections'><p></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/classKotlin.svg"/>&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a><br/></div>
