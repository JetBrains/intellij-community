class UberPropertyContainer {
    open class PropertyContainerA {
        open var propModifierB = 0
        protected open var propModifierG = AuxClassA()
        final val propModifierQ = object : AuxFaceA {}
    }
    interface PropertyContainerB {
        val propModifierC: Int
    }
    class PropertyContainerC : PropertyContainerA(), PropertyContainerB {
        override var propModifierB = 0
        override val propModifierC = 0
        lateinit var propModifierE: String
        inline val propModifierK: Int
            get() = 0

        var AuxClassA.propReceiverA
            get() = 0
            set(value) {}
        val AuxClassA?.propReceiverB
            get() = 0

        var <@AuxAnnA X> AuxFaceD<X>.propTypeParamD: Int
            get() = 0
            set(value) {}
        @Suppress("MISPLACED_TYPE_PARAMETER_CONSTRAINTS") val <X: AuxFaceA> AuxFaceD<X>.<caret>propTypeParamI: Int where X: AuxFaceB
            get() = 0
        val <X> AuxFaceD<X>.propTypeParamJ: Int where X: AuxFaceA, X: AuxFaceB
            get() = 0
    }
}
//INFO: <div class='definition'><pre><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://kotlin.Suppress">Suppress</a></span>(<span style="color:#000000;">names</span><span style=""> = </span><span style="">[</span><span style="color:#008000;font-weight:bold;">"MISPLACED_TYPE_PARAMETER_CONSTRAINTS"</span><span style="">]</span>)
//INFO: <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="">&lt;</span><span style="color:#20999d;">X</span><span style="">&gt;</span> <span style="color:#000000;"><a href="psi_element://AuxFaceD">AuxFaceD</a></span><span style="">&lt;</span><span style="color:#20999d;"><a href="psi_element://UberPropertyContainer.PropertyContainerC.propTypeParamI.X">X</a></span><span style="">&gt;</span><span style="">.</span><span style="color:#660e7a;font-weight:bold;">propTypeParamI</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span> <span style="color:#000080;font-weight:bold;">where</span> <span style="color:#20999d;">X</span><span style=""> : </span><span style="color:#000000;"><a href="psi_element://AuxFaceA">AuxFaceA</a></span>, <span style="color:#20999d;">X</span><span style=""> : </span><span style="color:#000000;"><a href="psi_element://AuxFaceB">AuxFaceB</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://UberPropertyContainer.PropertyContainerC"><code><span style="color:#000000;">UberPropertyContainer.PropertyContainerC</span></code></a><br/></div>
