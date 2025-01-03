class C {
    private val name: String = "kodee"
    fun foo() {
        print(<caret>name.length)
    }
}

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">private</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">name</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span> <span style=""> = </span><span style="color:#008000;font-weight:bold;">"kodee"</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://C"><code><span style="color:#000000;">C</span></code></a><br/></div>

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">private</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">name</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://C"><code><span style="color:#000000;">C</span></code></a><br/></div>
