class UberContainer {
    open class FunctionContainerC {
        open fun funModifierC(p: AuxFaceA): Number = 0
        protected open fun <caret>funModifierF() {}
    }
    interface FunctionContainerD {
        fun funModifierD(p: CharSequence): CharSequence
        fun funBodyReturnF(p: String) = p.length
    }
    class FunctionContainerE : FunctionContainerC(), FunctionContainerD {
        override fun funModifierC(p: AuxFaceA): Int = 0
        override fun funModifierD(p: CharSequence): String = ""
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">protected</span> <span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">funModifierF</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://UberContainer.FunctionContainerC"><code><span style="color:#000000;">UberContainer.FunctionContainerC</span></code></a><br/></div>
