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
    fun samplesA() {}

    /**
     * @sample unresolved
     */
    val <caret>samplesB = 0

}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">samplesB</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span><span style=""> = </span><span style="color:#0000ff;">0</span></pre></div><table class='sections'><tr><td valign='top' class='section'><p>Samples:</td><td valign='top'><p><a href="psi_element://unresolved"><code><span style="color:#4585be;">unresolved</span></code></a><pre><code><span style="color:#808080;font-style:italic;">//&#32;Unresolved</span></code></pre></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://Samples"><code><span style="color:#000000;">Samples</span></code></a><br/></div>
