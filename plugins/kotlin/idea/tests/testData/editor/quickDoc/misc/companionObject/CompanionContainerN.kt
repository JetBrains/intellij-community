class CompanionContainerN {
    companion <caret>object : suspend (AuxFaceA) -> AuxFaceB {
        override suspend fun invoke(p1: AuxFaceA) = object : AuxFaceB {}
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">companion</span> <span style="color:#000080;font-weight:bold;">object</span> <span style="color:#808080;font-style:italic;">of </span><span style="color:#000000;">CompanionContainerN</span> : <span style="color:#000080;font-weight:bold;">suspend</span> <span style="">(</span><span style="color:#000000;"><a href="psi_element://AuxFaceA">AuxFaceA</a></span><span style="">) </span><span style="">-&gt;</span> <span style="color:#000000;"><a href="psi_element://AuxFaceB">AuxFaceB</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://CompanionContainerN"><code><span style="color:#000000;">CompanionContainerN</span></code></a><br/></div>
