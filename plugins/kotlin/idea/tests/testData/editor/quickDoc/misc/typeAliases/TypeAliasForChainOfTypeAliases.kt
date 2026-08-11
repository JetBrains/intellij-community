/**
 * This is class A
 */
class A

typealias B = A

typealias C = B

fun test(c: C<caret>) {}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">typealias</span> <span style="color:#000000;">C</span> = <span style="color:#000000;"><a href="psi_element://B">B</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TypeAliasForChainOfTypeAliases.kt<br/></div><div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">typealias</span> <span style="color:#000000;">B</span> = <span style="color:#000000;"><a href="psi_element://A">A</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TypeAliasForChainOfTypeAliases.kt<br/></div><div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">A</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>This is class A</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TypeAliasForChainOfTypeAliases.kt<br/></div>
