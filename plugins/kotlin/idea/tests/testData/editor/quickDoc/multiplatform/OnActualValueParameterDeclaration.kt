// PLATFORM: Common
// FILE: Foo.kt
/**
 * Doc for expected class Foo
 *
 * @param param doc for expected `param` of function `foo`
 */
expect fun foo(param: String)

// PLATFORM: Jvm
// FILE: Foo.kt
// MAIN
actual fun foo(pa<caret>ram: String) {}

//INFO: <div class='definition'><pre><span style="color:#000000;">param</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>doc for expected <code><span style="">param</span></code> of function <code><span style="">foo</span></code></p></div><table class='sections'></table>