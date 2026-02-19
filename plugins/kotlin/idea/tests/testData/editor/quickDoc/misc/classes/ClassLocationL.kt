class LocationContainerA {
    class ClassLocationC
    inner class ClassLocationD
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
        interface <caret>ClassLocationL
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">interface</span> <span style="color:#000000;">ClassLocationL</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://LocationContainerA.Companion"><code><span style="color:#000000;">LocationContainerA.Companion</span></code></a><br/></div>
