
fun <T> elvisLike(x: T, y: T & Any): T & Any = x ?: y
fun someFun() {
    elvisLike<String>(<caret>)
}
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="">&lt;</span><span style="color:#20999d;">T</span><span style="">&gt;</span> <span style="color:#000000;">elvisLike</span>(
//INFO:     <span style="color:#000000;">x</span><span style="">: </span><span style="color:#20999d;"><a href="psi_element://elvisLike.T">T</a></span>,
//INFO:     <span style="color:#000000;">y</span><span style="">: </span><span style="color:#20999d;"><a href="psi_element://elvisLike.T">T & Any</a></span>
//INFO: )<span style="">: </span><span style="color:#20999d;"><a href="psi_element://elvisLike.T">T & Any</a></span></pre></div></pre></div><table class='sections'><p></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/kotlin_file.svg"/>&nbsp;DefinitelyNonNullType.kt<br/></div>
