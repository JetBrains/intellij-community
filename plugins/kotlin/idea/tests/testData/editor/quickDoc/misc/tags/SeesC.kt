/**
 * Inline comment prefix: [seenP] :inline comment postfix.
 *
 * Inline comment prefix: [SeesC.seenR]
 *
 * [SeesC.SeenS] :inline comment postfix.
 *
 * [SeenT]
 *
 * Inline comment prefix: [Companion] :inline comment postfix.
 */
class <caret>SeesC {
    var seenP = "p"
    fun seenR() = "r"
    class SeenS
    inner class SeenT
    companion object
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">SeesC</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Inline comment prefix: <a href="psi_element://seenP"><code><span style="color:#660e7a;font-weight:bold;">seenP</span></code></a> :inline comment postfix.</p>
//INFO: <p>Inline comment prefix: <a href="psi_element://SeesC.seenR"><code><span style="color:#000000;">SeesC</span><span style="">.</span><span style="color:#0000ff;">seenR</span></code></a></p>
//INFO: <p><a href="psi_element://SeesC.SeenS"><code><span style="color:#000000;">SeesC</span><span style="">.</span><span style="color:#0000ff;">SeenS</span></code></a> :inline comment postfix.</p>
//INFO: <p><a href="psi_element://SeenT"><code><span style="color:#0000ff;">SeenT</span></code></a></p>
//INFO: <p>Inline comment prefix: <a href="psi_element://Companion"><code><span style="color:#0000ff;">Companion</span></code></a> :inline comment postfix.</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;SeesC.kt<br/></div>
