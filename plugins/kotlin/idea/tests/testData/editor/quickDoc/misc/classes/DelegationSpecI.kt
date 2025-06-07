class <caret>DelegationSpecI : (AuxFaceA) -> AuxFaceB {
    override fun invoke(p1: AuxFaceA) = object : AuxFaceB {}
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">DelegationSpecI</span> : <span style="">(</span><span style="color:#000000;"><a href="psi_element://AuxFaceA">AuxFaceA</a></span><span style="">) </span><span style="">-&gt;</span> <span style="color:#000000;"><a href="psi_element://AuxFaceB">AuxFaceB</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;DelegationSpecI.kt<br/></div>
