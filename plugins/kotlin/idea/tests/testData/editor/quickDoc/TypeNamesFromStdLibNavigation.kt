class A {

}

fun foo(x : A) { }

fun main(args: Array<String>) {
    <caret>foo()
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">foo</span>(
//INFO:     <span style="color:#000000;">x</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://A">A</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;">Unit</span></pre></div></pre></div><table class='sections'><p></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/kotlin_file.svg"/>&nbsp;TypeNamesFromStdLibNavigation.kt<br/></div>
