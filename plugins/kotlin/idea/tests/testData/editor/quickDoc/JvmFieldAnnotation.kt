annotation class Anno
class JvmFieldContainerContainer {
    companion object {
        @JvmField @Anno val jvmF<caret>ield = 0
    }
}

//K2_INFO: <div class='definition'><pre><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Anno">Anno</a></span>
//K2_INFO: <span style="color:#808000;">@</span><span style="color:#000080;font-weight:bold;">field</span><span style="">:</span><span style="color:#808000;"><a href="psi_element://kotlin.jvm.JvmField">JvmField</a></span>
//K2_INFO: <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">jvmField</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span> <span style=""> = </span><span style="color:#0000ff;">0</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://JvmFieldContainerContainer.Companion"><code><span style="color:#000000;">JvmFieldContainerContainer.Companion</span></code></a><br/></div>

//INFO: <div class='definition'><pre><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Anno">Anno</a></span>
//INFO: <span style="color:#808000;">@</span><span style="color:#000080;font-weight:bold;">field</span><span style="color:#808000;">:</span><span style="color:#808000;"><a href="psi_element://kotlin.jvm.JvmField">JvmField</a></span> <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">jvmField</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://JvmFieldContainerContainer.Companion"><code><span style="color:#000000;">JvmFieldContainerContainer.Companion</span></code></a><br/></div>
