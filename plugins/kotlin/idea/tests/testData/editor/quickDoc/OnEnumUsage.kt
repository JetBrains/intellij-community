/**
 * Enum of 1, 2
 */
enum class SomeEnum(val i: Int) {
    One(1), Two(2);
}

fun use() {
    Some<caret>Enum.One
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">enum class</span> <span style="color:#000000;">SomeEnum</span></pre></div><div class='content'><p>Enum of 1, 2</p></div><table class='sections'></table>
