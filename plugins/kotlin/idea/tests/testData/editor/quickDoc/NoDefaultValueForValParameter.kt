class A {
    val p = ""

    fun m(a: String = p) {
        m<caret>("e")
    }
}

// IGNORE_K1
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">m</span>(
//INFO:     <span style="color:#000000;">a</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span><span style=""> = </span><span style="">p</span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://A"><code><span style="color:#000000;">A</span></code></a><br/></div>
