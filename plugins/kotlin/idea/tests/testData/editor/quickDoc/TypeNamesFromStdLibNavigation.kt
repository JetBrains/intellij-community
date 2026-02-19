class A {

}

fun foo(x : A) { }

fun main(args: Array<String>) {
    <caret>foo()
}

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">foo</span>(
//K2_INFO:     <span style="color:#000000;">x</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://A">A</a></span>
//K2_INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TypeNamesFromStdLibNavigation.kt<br/></div>


//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">foo</span>(
//INFO:     <span style="color:#000000;">x</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://A">A</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div></pre></div><table class='sections'><p></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TypeNamesFromStdLibNavigation.kt<br/></div>
