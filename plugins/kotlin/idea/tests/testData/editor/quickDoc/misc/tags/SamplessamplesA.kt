class Samples {
    fun sampledA() = "simple sample"
    fun sampledB(p: Int): Int {
        val q = p + 1
        return q + 2
    }

    class SampledContainerC {
        @Suppress("ReplacePrintlnWithLogging")
        fun sampledD(p: CharSequence) {
            println(p.length)
        }
    }

    /**
     * @sample sampledA
     * @sample sampledB
     * @sample SampledContainerC.sampledD
     */
    fun <caret>samplesA() {}

    /**
     * @sample unresolved
     */
    val samplesB = 0

}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">samplesA</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><table class='sections'><tr><td valign='top' class='section'><p>Samples:</td><td valign='top'><p><a href="psi_element://sampledA"><code><span style="color:#0000ff;">sampledA</span></code></a><pre><code><span style="color:#008000;font-weight:bold;">"simple&#32;sample"</span></code></pre><p><a href="psi_element://sampledB"><code><span style="color:#0000ff;">sampledB</span></code></a><pre><code><span style="color:#000080;font-weight:bold;">val&#32;</span><span style="">q&#32;=&#32;p&#32;+&#32;</span><span style="color:#0000ff;">1<br></span><span style="color:#000080;font-weight:bold;">return&#32;</span><span style="">q&#32;+&#32;</span><span style="color:#0000ff;">2</span></code></pre><p><a href="psi_element://SampledContainerC.sampledD"><code><span style="color:#000000;">SampledContainerC</span><span style="">.</span><span style="color:#0000ff;">sampledD</span></code></a><pre><code><span style="">println(p.length)</span></code></pre></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://Samples"><code><span style="color:#000000;">Samples</span></code></a><br/></div>
