/**
 * @see C
 * @see D
 * @see <a href="https://kotl.in">kotlin</a>
 */
fun testMethod() {

}

class C {
}

class D {
}

fun test() {
    <caret>testMethod(1, "value")
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">testMethod</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><table class='sections'><tr><td valign='top' class='section'><p>See Also:</td><td valign='top'><a href="psi_element://C"><code><span style="color:#0000ff;">C</span></code></a>,<br><a href="psi_element://D"><code><span style="color:#0000ff;">D</span></code></a>,<br><a href="https://kotl.in">kotlin</a></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnMethodUsageWithSee.kt<br/></div>
