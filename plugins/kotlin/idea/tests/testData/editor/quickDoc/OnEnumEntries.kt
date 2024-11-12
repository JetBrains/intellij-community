// WITH_STDLIB

enum class E {
    A
}

fun use() {
    E.entr<caret>ies
}


//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">entries</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.enums.EnumEntries">EnumEntries</a></span><span style="">&lt;</span><span style="color:#000000;"><a href="psi_element://E">E</a></span><span style="">&gt;</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Returns an immutable <a href="psi_element://kotlin.enums.EnumEntries"><code><span style="color:#000000;">kotlin</span><span style="">.</span><span style="color:#000000;">enums</span><span style="">.</span><span style="color:#0000ff;">EnumEntries</span></code></a> list containing the constants of this enum type, in the order they're declared.</p></div><table class='sections'></table>
