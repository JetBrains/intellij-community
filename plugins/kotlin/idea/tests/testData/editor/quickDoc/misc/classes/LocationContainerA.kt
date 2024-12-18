class <caret>LocationContainerA {
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
        interface ClassLocationL
    }
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">LocationContainerA</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;LocationContainerA.kt<br/></div>
