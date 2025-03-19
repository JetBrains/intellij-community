class FunctionContainerH<X> {
    fun <Y> funTypeParamC(x: X, y: Y) {}
    fun <X: AuxFaceA?> funTypeParamF(p: X) {}
    fun <X: () -> Unit> funTypeParamI(p: X) = p()
    fun <X> funTypeParamL(p: X) where X: AuxFaceA {}
    @Suppress("MISPLACED_TYPE_PARAMETER_CONSTRAINTS") fun <X: AuxFaceA> funTypeParamM(p: X) where X: AuxFaceB {}

    fun <Y> ((X, Y) -> Unit).<caret>funReceiverD(x: X, y: Y) = this(x, y)
    fun ((AuxFaceA) -> Unit).funReceiverE(p: AuxFaceA) = this(p)

    inline fun funValParamD(fn0: () -> Unit, noinline fnn: () -> Unit, crossinline fnc: () -> Unit) { fn0(); fnn(); fnc(); }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="">&lt;</span><span style="color:#20999d;">Y</span><span style="">&gt;</span> <span style="">(</span><span style="">(</span><span style="color:#20999d;"><a href="psi_element://FunctionContainerH.X">X</a></span>, <span style="color:#20999d;"><a href="psi_element://FunctionContainerH.funReceiverD.Y">Y</a></span><span style="">) </span><span style="">-&gt;</span> <span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span><span style="">)</span><span style="">.</span><span style="color:#000000;">funReceiverD</span>(
//INFO:     <span style="color:#000000;">x</span><span style="">: </span><span style="color:#20999d;"><a href="psi_element://FunctionContainerH.X">X</a></span>,
//INFO:     <span style="color:#000000;">y</span><span style="">: </span><span style="color:#20999d;"><a href="psi_element://FunctionContainerH.funReceiverD.Y">Y</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://FunctionContainerH"><code><span style="color:#000000;">FunctionContainerH</span></code></a><br/></div>
