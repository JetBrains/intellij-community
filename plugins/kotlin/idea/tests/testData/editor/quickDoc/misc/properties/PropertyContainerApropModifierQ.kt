class UberPropertyContainer {
    open class PropertyContainerA {
        open var propModifierB = 0
        protected open var propModifierG = AuxClassA()
        final val <caret>propModifierQ = object : AuxFaceA {}
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
        @Suppress("MISPLACED_TYPE_PARAMETER_CONSTRAINTS") val <X: AuxFaceA> AuxFaceD<X>.propTypeParamI: Int where X: AuxFaceB
            get() = 0
        val <X> AuxFaceD<X>.propTypeParamJ: Int where X: AuxFaceA, X: AuxFaceB
            get() = 0
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">propModifierQ</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://AuxFaceA">AuxFaceA</a></span> <span style=""> = </span><span style="color:#000080;font-weight:bold;">object&#32;</span><span style="">:&#32;AuxFaceA&#32;{}</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://UberPropertyContainer.PropertyContainerA"><code><span style="color:#000000;">UberPropertyContainer.PropertyContainerA</span></code></a><br/></div>
