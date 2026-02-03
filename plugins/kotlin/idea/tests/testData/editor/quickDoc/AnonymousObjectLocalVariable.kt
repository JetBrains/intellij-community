interface OurFace
open class OurClass

fun context() {
    val v = object : OurClass(), OurFace {}
    v<caret>
}
//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">local</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#000000;">v</span><span style="">: </span>&lt;anonymous object : <span style="color:#000000;"><a href="psi_element://OurClass">OurClass</a></span>, <span style="color:#000000;"><a href="psi_element://OurFace">OurFace</a></span>&gt;</pre></div>
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">val</span> <span style="color:#000000;">v</span><span style="">: </span><span style="color:#000000;">&lt;anonymous object : <a href="psi_element://OurClass">OurClass</a>, <a href="psi_element://OurFace">OurFace</a>&gt;</span></pre></div>
