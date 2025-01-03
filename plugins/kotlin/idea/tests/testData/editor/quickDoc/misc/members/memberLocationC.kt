class LocationContainerE {
    var <caret>memberLocationC = 0
    fun memberLocationD() {}

    class LocationContainerF {
        var memberLocationE = 0
        fun memberLocationF() {}
    }

    inner class LocationContainerG {
        var memberLocationG = 0
        fun memberLocationH() {}
    }

    companion object {
        var memberLocationI = 0
        fun memberLocationJ() {}
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">var</span> <span style="color:#660e7a;font-weight:bold;">memberLocationC</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span> <span style=""> = </span><span style="color:#0000ff;">0</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://LocationContainerE"><code><span style="color:#000000;">LocationContainerE</span></code></a><br/></div>
