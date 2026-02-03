enum class EnumE(val p1: String, var p2: AuxFaceA) {
    <caret>EE_VA_E("a", object : AuxFaceA {})
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">enum entry</span> <span style="color:#000000;">EE_VA_E</span><br><span style="color:#808080;font-style:italic;">// </span><span style="color:#808080;font-style:italic;">Enum constant ordinal: 0</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://EnumE"><code><span style="color:#000000;">EnumE</span></code></a><br/></div>
