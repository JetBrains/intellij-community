class LocationContainerA {
    class ClassLocationC
    inner class ClassLocationD
    interface ClassLocationE
    enum class ClassLocationF
    annotation class ClassLocationG
    data class <caret>ClassLocationH(val afa: AuxFaceA)

    class LocationContainerB {
        class ClassLocationI
        interface ClassLocationJ
    }

    companion object {
        class ClassLocationK
        interface ClassLocationL
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">data</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">ClassLocationH</span>(
//INFO:     <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">afa</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://AuxFaceA">AuxFaceA</a></span>
//INFO: )</pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://LocationContainerA"><code><span style="color:#000000;">LocationContainerA</span></code></a><br/></div>
