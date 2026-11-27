open class Parent {
    open fun example(count: Int = 1) {}
}
class Child : Parent() {
    override fun exa<caret>mple(count: Int) {}
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">example</span><span style="">(</span><span style="color:#000000;">count</span><span style="">:</span> <span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span> <span style="">=</span> ...<span style="">)</span><span style="">:</span> <span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://Child"><code><span style="color:#000000;">Child</span></code></a><br/></div>
