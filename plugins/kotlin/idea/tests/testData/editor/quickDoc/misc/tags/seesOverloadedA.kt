
class SeenOverloadedA() {
    constructor(p: Int): this()
    constructor(p: String): this()

    fun seenOverloadedB(p: AuxFaceA) {}
    fun seenOverloadedB(p1: AuxFaceA, p2: AuxFaceB) {}
}

fun seenOverloadedC(p: Int) {}
fun seenOverloadedC(p: AuxFaceA) {}

/**
 * KDoc has no syntax for specific overload.
 *
 * Prefix [SeenOverloadedA] postfix.
 *
 * Prefix [SeenOverloadedA.seenOverloadedB] postfix.
 *
 * Prefix [seenOverloadedC] postfix.
 *
 * @see SeenOverloadedA
 * @see SeenOverloadedA.seenOverloadedB
 * @see seenOverloadedC
 */
val <caret>seesOverloadedA = 0
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">seesOverloadedA</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span><span style=""> = </span><span style="color:#0000ff;">0</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>KDoc has no syntax for specific overload.</p>
//INFO: <p>Prefix <a href="psi_element://SeenOverloadedA"><code><span style="color:#0000ff;">SeenOverloadedA</span></code></a> postfix.</p>
//INFO: <p>Prefix <a href="psi_element://SeenOverloadedA.seenOverloadedB"><code><span style="color:#000000;">SeenOverloadedA</span><span style="">.</span><span style="color:#0000ff;">seenOverloadedB</span></code></a> postfix.</p>
//INFO: <p>Prefix <a href="psi_element://seenOverloadedC"><code><span style="color:#0000ff;">seenOverloadedC</span></code></a> postfix.</p></div><table class='sections'><tr><td valign='top' class='section'><p>See Also:</td><td valign='top'><a href="psi_element://SeenOverloadedA"><code><span style="color:#0000ff;">SeenOverloadedA</span></code></a>,<br><a href="psi_element://SeenOverloadedA.seenOverloadedB"><code><span style="color:#000000;">SeenOverloadedA</span><span style="">.</span><span style="color:#0000ff;">seenOverloadedB</span></code></a>,<br><a href="psi_element://seenOverloadedC"><code><span style="color:#0000ff;">seenOverloadedC</span></code></a></td></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;seesOverloadedA.kt<br/></div>
