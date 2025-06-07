class FunctionContainerA {
    fun funBodyReturnB() {}
    fun funBodyReturnC(): AuxFaceA = object : AuxFaceA {}

    suspend fun funModifierA() {}
    fun funModifierB() {}
    private fun funModifierH(p: AuxFaceA) {}
    @AuxAnnA fun funModifierJ() {}
    @JvmName("funModifierL") operator fun plus(p: AuxClassA) = hashCode()
    inline fun funModifierN(afa: AuxFaceA, fn: (AuxFaceA) -> AuxFaceB) = fn(afa)
    final fun funModifierP(p: AuxFaceA) {}

    fun <X: AuxFaceA> funTypeParamE(p: X) {}
    fun <X> funTypeParamO(p: X) where X: AuxClassA, X: AuxFaceA, X: AuxFaceB {}

    fun AuxFaceD<AuxFaceA>.<caret>funReceiverB(p: AuxFaceB) {}

    fun funValParamB(@AuxAnnA p: String) {}
    @JvmOverloads fun funValParamF(aca: AuxClassA = AuxClassA(), afa: AuxFaceA = object : AuxFaceA {}) {}
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;"><a href="psi_element://AuxFaceD">AuxFaceD</a></span><span style="">&lt;</span><span style="color:#000000;"><a href="psi_element://AuxFaceA">AuxFaceA</a></span><span style="">&gt;</span><span style="">.</span><span style="color:#000000;">funReceiverB</span>(
//INFO:     <span style="color:#000000;">p</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://AuxFaceB">AuxFaceB</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://FunctionContainerA"><code><span style="color:#000000;">FunctionContainerA</span></code></a><br/></div>
