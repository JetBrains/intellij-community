abstract class PropertyContainerF {
    abstract val <caret>propModifierQ: AuxClassA

    abstract var <X> AuxFaceD<X>.propReceiverC: X
    abstract val AuxFaceD<String>.propReceiverD: AuxFaceA

    abstract var <X> AuxFaceD<X>.propTypeParamA: AuxFaceA
    abstract val <X: AuxFaceA> AuxFaceD<X>.propTypeParamE: Int
    abstract var <X: AuxFaceA?> AuxFaceD<X>.propTypeParamF: Int
    abstract val <X: AuxFaceA, Y: AuxFaceB> AuxFaceE<X, Y>.propTypeParamF: X
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">abstract</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">propModifierQ</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://AuxClassA">AuxClassA</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://PropertyContainerF"><code><span style="color:#000000;">PropertyContainerF</span></code></a><br/></div>
