/**
 * This is class A
 */
class A

/**
 * This is class B
 */
typealias B = A

fun test(b: B<caret>) {}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">typealias</span> <span style="color:#000000;">B</span> = <span style="color:#000000;"><a href="psi_element://A">A</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>This is class B</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TypeAliasWithKDocForClassWithKDoc.kt<br/></div>
