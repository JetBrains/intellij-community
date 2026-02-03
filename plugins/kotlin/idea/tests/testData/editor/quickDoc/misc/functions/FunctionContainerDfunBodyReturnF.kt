class UberContainer {
    open class FunctionContainerC {
        open fun funModifierC(p: AuxFaceA): Number = 0
        protected open fun funModifierF() {}
    }
    interface FunctionContainerD {
        fun funModifierD(p: CharSequence): CharSequence
        fun <caret>funBodyReturnF(p: String) = p.length
    }
    class FunctionContainerE : FunctionContainerC(), FunctionContainerD {
        override fun funModifierC(p: AuxFaceA): Int = 0
        override fun funModifierD(p: CharSequence): String = ""
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">open</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">funBodyReturnF</span>(
//INFO:     <span style="color:#000000;">p</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://UberContainer.FunctionContainerD"><code><span style="color:#000000;">UberContainer.FunctionContainerD</span></code></a><br/></div>
