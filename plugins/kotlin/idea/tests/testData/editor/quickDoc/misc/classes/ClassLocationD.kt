class LocationContainerA {
    class ClassLocationC
    inner class <caret>ClassLocationD
    interface ClassLocationE
    enum class ClassLocationF
    annotation class ClassLocationG
    data class ClassLocationH(val afa: AuxFaceA)

    class LocationContainerB {
        class ClassLocationI
        interface ClassLocationJ
    }

    companion object {
        class ClassLocationK
        interface ClassLocationL
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">inner</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">ClassLocationD</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://LocationContainerA"><code><span style="color:#000000;">LocationContainerA</span></code></a><br/></div>
