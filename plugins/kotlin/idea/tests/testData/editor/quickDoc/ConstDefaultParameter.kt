const val default = "bar"

fun foo(s: String = default) {
    foo<caret>()
}

// IGNORE_K1
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">foo</span>(
//INFO:     <span style="color:#000000;">s</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span><span style=""> = </span><span style="color:#000000;">default</span><span style=""> = </span><span style="color:#008000;font-weight:bold;">"bar"</span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;ConstDefaultParameter.kt<br/></div>
