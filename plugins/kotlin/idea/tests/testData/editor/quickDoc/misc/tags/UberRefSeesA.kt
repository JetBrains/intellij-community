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
    class <caret>SeesA {
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
    fun seesB() {}
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">SeesA</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Long comment to make the popup wider.</p></div><table class='sections'><tr><td valign='top' class='section'><p>See Also:</td><td valign='top'><a href="psi_element://unresolved"><code><span style="color:#4585be;">unresolved</span></code></a>,<br><a href="psi_element://SeenA"><code><span style="color:#0000ff;">SeenA</span></code></a>,<br><a href="psi_element://UberRef.SeenB"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#0000ff;">SeenB</span></code></a>,<br><a href="psi_element://String"><code><span style="color:#0000ff;">String</span></code></a>,<br><a href="psi_element://kotlin.Int"><code><span style="color:#000000;">kotlin</span><span style="">.</span><span style="color:#0000ff;">Int</span></code></a>,<br><a href="psi_element://seenC"><code><span style="color:#660e7a;font-weight:bold;">seenC</span></code></a>,<br><a href="psi_element://UberRef.seenD"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#660e7a;font-weight:bold;">seenD</span></code></a>,<br><a href="psi_element://seenE"><code><span style="color:#0000ff;">seenE</span></code></a>,<br><a href="psi_element://UberRef.seenF"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#0000ff;">seenF</span></code></a>,<br><a href="psi_element://SeenContainerA.SeenG"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">SeenG</span></code></a>,<br><a href="psi_element://UberRef.SeenContainerA.SeenH"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">SeenH</span></code></a>,<br><a href="psi_element://SeenContainerA.SeenI"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">SeenI</span></code></a>,<br><a href="psi_element://qUberRef.SeenContainerA.SeenJ"><code><span style="color:#000000;">qUberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#4585be;">SeenJ</span></code></a>,<br><a href="psi_element://SeenContainerA.Companion"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">Companion</span></code></a>,<br><a href="psi_element://UberRef.SeenContainerA.seenL"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#660e7a;font-weight:bold;">seenL</span></code></a>,<br><a href="psi_element://SeenContainerA.seenM"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#660e7a;font-weight:bold;">seenM</span></code></a>,<br><a href="psi_element://UberRef.SeenContainerA.seenN"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">seenN</span></code></a>,<br><a href="psi_element://SeenContainerA.seenO"><code><span style="color:#000000;">SeenContainerA</span><span style="">.</span><span style="color:#0000ff;">seenO</span></code></a>,<br><a href="psi_element://UberRef.SeenContainerB.SeenK"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeenContainerB</span><span style="">.</span><span style="color:#0000ff;">SeenK</span></code></a>,<br><a href="psi_element://seenP"><code><span style="color:#660e7a;font-weight:bold;">seenP</span></code></a>,<br><a href="psi_element://SeesA.seenR"><code><span style="color:#000000;">SeesA</span><span style="">.</span><span style="color:#0000ff;">seenR</span></code></a>,<br><a href="psi_element://UberRef.SeesA.SeenS"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#000000;">SeesA</span><span style="">.</span><span style="color:#0000ff;">SeenS</span></code></a>,<br><a href="psi_element://SeenT"><code><span style="color:#0000ff;">SeenT</span></code></a>,<br><a href="psi_element://Companion"><code><span style="color:#0000ff;">Companion</span></code></a>,<br><a href="psi_element://UberRef.another"><code><span style="color:#000000;">UberRef</span><span style="">.</span><span style="color:#4585be;">another</span></code></a></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://UberRef"><code><span style="color:#000000;">UberRef</span></code></a><br/></div>
