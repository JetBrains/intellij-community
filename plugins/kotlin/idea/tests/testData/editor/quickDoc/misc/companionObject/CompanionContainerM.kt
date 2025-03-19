class CompanionContainerM {
    companion object <caret>CompanionM : () -> Unit {
        override fun invoke() {}
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">companion</span> <span style="color:#000080;font-weight:bold;">object</span> <span style="color:#000000;">CompanionM</span> : <span style="">(</span><span style="">) </span><span style="">-&gt;</span> <span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://CompanionContainerM"><code><span style="color:#000000;">CompanionContainerM</span></code></a><br/></div>
