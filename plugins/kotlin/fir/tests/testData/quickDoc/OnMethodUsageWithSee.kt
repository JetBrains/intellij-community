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

//INFO: <div class='definition'><pre>fun testMethod(): Unit</pre></div><table class='sections'><tr><td valign='top' class='section'><p>See Also:</td><td valign='top'><a href="psi_element://C"><code><span style="color:#0000ff;">C</span></code></a>,<br><a href="psi_element://D"><code><span style="color:#0000ff;">D</span></code></a>,<br><a href="https://kotl.in">kotlin</a></td></table><div class='bottom'><icon src="file"/>&nbsp;OnMethodUsageWithSee.kt<br/></div>
