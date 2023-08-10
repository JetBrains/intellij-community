enum class E {
    A
}

fun use() {
    E.valueOf<caret>("A")
}


//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">valueOf</span>(
//INFO:     <span style="color:#000000;">value</span><span style="">: </span><span style="color:#000000;">String</span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://E">E</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Returns the enum constant of this type with the specified name. The string must match exactly an identifier used to declare an enum constant in this type. (Extraneous whitespace characters are not permitted.)</p></div><table class='sections'><tr><td valign='top' class='section'><p>Throws:</td><td valign='top'><p><code><a href="psi_element://IllegalArgumentException"><code style='font-size:96%;'><span style="color:#0000ff;">IllegalArgumentException</span></code></a></code> - if this enum type has no constant with the specified name</td></table>
