enum class E {

}

fun use() {
    E.values<caret>()
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">values</span>()<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Array">Array</a></span><span style="">&lt;</span><span style="color:#000000;"><a href="psi_element://E">E</a></span><span style="">&gt;</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Returns an array containing the constants of this enum type, in the order they're declared. This method may be used to iterate over the constants.</p>
//INFO: <p>The function returns a new instance of the array on every call. The array could be mutated, so working with it may also require defensive copying. Consider using <code><span style="">entries</span></code> property as a more efficient alternative returning an immutable list of enum entries.</p></div><table class='sections'></table>
