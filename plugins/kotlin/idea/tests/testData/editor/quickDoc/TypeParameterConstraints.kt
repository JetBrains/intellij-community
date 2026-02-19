interface I1
interface I2
fun <T: I1> ba<caret>r() where T: I2 {
}


//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="">&lt;</span><span style="color:#20999d;">T</span><span style="">&gt;</span> <span style="color:#000000;">bar</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span> <span style="color:#000080;font-weight:bold;">where</span> <span style="color:#20999d;">T</span><span style=""> : </span><span style="color:#000000;"><a href="psi_element://I1">I1</a></span>, <span style="color:#20999d;">T</span><span style=""> : </span><span style="color:#000000;"><a href="psi_element://I2">I2</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TypeParameterConstraints.kt<br/></div>

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="">&lt;</span><span style="color:#20999d;">T</span><span style=""> : </span><span style="color:#000000;"><a href="psi_element://I1">I1</a></span><span style="">&gt;</span> <span style="color:#000000;">bar</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span> <span style="color:#000080;font-weight:bold;">where</span> <span style="color:#20999d;">T</span><span style=""> : </span><span style="color:#000000;"><a href="psi_element://I2">I2</a></span></pre></div></pre></div><table class='sections'><p></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TypeParameterConstraints.kt<br/></div>
