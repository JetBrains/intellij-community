interface A {
    suspend fun f()
}

class B: A {
    override suspend fun <caret>f() {}
}

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">suspend</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">f</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://B"><code><span style="color:#000000;">B</span></code></a><br/></div>
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">suspend</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">f</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div></pre></div><table class='sections'><p><tr><td valign='top' class='section'><p>Specified by:</td><td valign='top'><p><a href="psi_element://A#f(kotlin.coroutines.Continuation)"><code><span style="color:#000000;">f</span></code></a> in interface <a href="psi_element://A"><code><span style="color:#000000;">A</span></code></a></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://B"><code><span style="color:#000000;">B</span></code></a><br/></div>
