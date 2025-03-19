class UberRef {
    class SeenA
    class SeenB
    val seenC = "c"
    var seenD = "d"
    fun seenE() = "e"
    fun seenF() = "f"
    class SeenContainerA {
        class SeenG
        class SeenH
        inner class SeenI
        inner class SeenJ
        companion object
        val seenL = "L"
        var seenM = "M"
        fun seenN() = "N"
        fun seenO() = "O"
    }

    class SeenContainerB {
        companion object SeenK
    }

    /**
     * Long comment to make the popup wider.
     *
     * @see unresolved
     * @see SeenA
     * @see [UberRef.SeenB]
     * @see [String]
     * @see kotlin.Int
     * @see seenC arbitrary words: not rendered
     * @see [UberRef.seenD]
     * @see [seenE]
     * @see UberRef.seenF
     * @see SeenContainerA.SeenG
     * @see [UberRef.SeenContainerA.SeenH]
     * @see [SeenContainerA.SeenI]
     * @see qUberRef.SeenContainerA.SeenJ
     * @see SeenContainerA.Companion
     * @see [UberRef.SeenContainerA.seenL]
     * @see [SeenContainerA.seenM]
     * @see UberRef.SeenContainerA.seenN
     * @see SeenContainerA.seenO
     * @see [UberRef.SeenContainerB.SeenK]
     * @see [seenP]
     * @see SeesA.seenR
     * @see UberRef.SeesA.SeenS
     * @see [SeenT]
     * @see [Companion]
     * @see UberRef.another
     */
    class SeesA {
        var seenP = "p"
        fun seenR() = "r"
        class SeenS
        inner class SeenT
        companion object
    }
    /**
     * Inline comment prefix: [SeenA] :inline comment postfix.
     *
     * Inline comment prefix: [Alternate text for SeenB.][UberRef.SeenB]
     *
     * [String] :inline comment postfix.
     *
     * [Alternate text for Int.][kotlin.Int]
     *
     * Inline comment prefix: [seenC] arbitrary words: not rendered :inline comment postfix.
     *
     * Inline comment prefix: [UberRef.seenD]
     *
     * [seenE] :inline comment postfix.
     *
     * [And extra alternate text block.][Alternate text for seenF.][UberRef.seenF]
     *
     * Inline comment prefix: [SeenContainerA.SeenG]
     *
     * [UberRef.SeenContainerA.SeenH] :inline comment postfix.
     *
     * [SeenContainerA.SeenI]
     *
     * Inline comment prefix: [UberRef.SeenContainerA.SeenJ] :inline comment postfix.
     *
     * Inline comment prefix: [SeenContainerA.Companion]
     *
     * [UberRef.SeenContainerA.seenL] :inline comment postfix.
     *
     * [SeenContainerA.seenM]
     *
     * Inline comment prefix: [UberRef.SeenContainerA.seenN] :inline comment postfix.
     *
     * Inline comment prefix: [SeenContainerA.seenO]
     *
     * [UberRef.SeenContainerB.SeenK] :inline comment postfix.
     *
     * [seenP]
     *
     * Inline comment prefix: [SeesA.seenR] :inline comment postfix.
     *
     * Inline comment prefix: [UberRef.SeesA.SeenS]
     *
     * [SeenT] :inline comment postfix.
     *
     * [Companion]
     *
     * Inline comment prefix: [UberRef.another] :inline comment postfix.
     */
    fun <caret>seesB() {}
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">seesB</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Inline comment prefix: <a href="psi_element://SeenA"><code><span style="color:#0000ff;">SeenA</span></code></a> :inline comment postfix.</p>
//INFO: <p>Inline comment prefix: <a href="psi_element://UberRef.SeenB"><code><span style="color:#000000;">Alternate text for SeenB</span><span style="">.</span><span style="color:#0000ff;"></span></code></a></p>
//INFO: <p><a href="psi_element://String"><code><span style="color:#0000ff;">String</span></code></a> :inline comment postfix.</p>
//INFO: <p><a href="psi_element://kotlin.Int"><code><span style="color:#000000;">Alternate text for Int</span><span style="">.</span><span style="color:#0000ff;"></span></code></a></p>
//INFO: <p>Inline comment prefix: <a href="psi_element://seenC"><code><span style="color:#660e7a;font-weight:bold;">seenC</span></code></a> arbitrary words: not rendered :inline comment postfix.</p>
//INFO: <p>Inline comment prefix: <a href="psi_element://UberRef.seenD"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#660e7a;font-weight:bold;">seenD</span></code></a></p>
//INFO: <p><a href="psi_element://seenE"><code><span style="color:#0000ff;">seenE</span></code></a> :inline comment postfix.</p>
//INFO: <p><span style="border:1px solid;border-color:#ff0000;">Alternate text for seenF.</span><a href="psi_element://UberRef.seenF"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#0000ff;">seenF</span></code></a></p>
//INFO: <p>Inline comment prefix: <a href="psi_element://SeenContainerA.SeenG"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">SeenG</span></code></a></p>
//INFO: <p><a href="psi_element://UberRef.SeenContainerA.SeenH"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">SeenH</span></code></a> :inline comment postfix.</p>
//INFO: <p><a href="psi_element://SeenContainerA.SeenI"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">SeenI</span></code></a></p>
//INFO: <p>Inline comment prefix: <a href="psi_element://UberRef.SeenContainerA.SeenJ"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">SeenJ</span></code></a> :inline comment postfix.</p>
//INFO: <p>Inline comment prefix: <a href="psi_element://SeenContainerA.Companion"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">Companion</span></code></a></p>
//INFO: <p><a href="psi_element://UberRef.SeenContainerA.seenL"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#660e7a;font-weight:bold;">seenL</span></code></a> :inline comment postfix.</p>
//INFO: <p><a href="psi_element://SeenContainerA.seenM"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#660e7a;font-weight:bold;">seenM</span></code></a></p>
//INFO: <p>Inline comment prefix: <a href="psi_element://UberRef.SeenContainerA.seenN"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">seenN</span></code></a> :inline comment postfix.</p>
//INFO: <p>Inline comment prefix: <a href="psi_element://SeenContainerA.seenO"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">seenO</span></code></a></p>
//INFO: <p><a href="psi_element://UberRef.SeenContainerB.SeenK"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerB</span><span style="">.</span><span style="color:#0000ff;">SeenK</span></code></a> :inline comment postfix.</p>
//INFO: <p><span style="border:1px solid;border-color:#ff0000;">seenP</span></p>
//INFO: <p>Inline comment prefix: <a href="psi_element://SeesA.seenR"><code><span style="color:#000000;">SeesA</span><span style="">.</span><span style="color:#0000ff;">seenR</span></code></a> :inline comment postfix.</p>
//INFO: <p>Inline comment prefix: <a href="psi_element://UberRef.SeesA.SeenS"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeesA</span><span style="">.</span><span style="color:#0000ff;">SeenS</span></code></a></p>
//INFO: <p><span style="border:1px solid;border-color:#ff0000;">SeenT</span> :inline comment postfix.</p>
//INFO: <p><span style="border:1px solid;border-color:#ff0000;">Companion</span></p>
//INFO: <p>Inline comment prefix: <span style="border:1px solid;border-color:#ff0000;">UberRef.another</span> :inline comment postfix.</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://UberRef"><code><span style="color:#000000;">UberRef</span></code></a><br/></div>
