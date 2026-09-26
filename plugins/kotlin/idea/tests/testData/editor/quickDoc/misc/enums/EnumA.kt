enum class EnumA {
    EE_SIMPLE,
    @AuxAnnA <caret>EE_MOD_A,
}
//INFO: <div class='definition'><pre><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://AuxAnnA">AuxAnnA</a></span>
//INFO: <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">enum entry</span> <span style="color:#000000;">EE_MOD_A</span><br><span style="color:#808080;font-style:italic;">// </span><span style="color:#808080;font-style:italic;">Enum constant ordinal: 1</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://EnumA"><code><span style="color:#000000;">EnumA</span></code></a><br/></div>
