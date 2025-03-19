interface AuxFaceA
open class AuxClassD(afa: AuxFaceA)
class Delegati<caret>onSpecB : AuxClassD(object : AuxFaceA {})

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">DelegationSpecB</span> : <span style="color:#000000;"><a href="psi_element://AuxClassD">AuxClassD</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;SuperConstructorWithArgs.kt<br/></div>
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">DelegationSpecB</span>
//INFO:     <span style="">: </span><span style="color:#000000;"><a href="psi_element://AuxClassD">AuxClassD</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;SuperConstructorWithArgs.kt<br/></div>
