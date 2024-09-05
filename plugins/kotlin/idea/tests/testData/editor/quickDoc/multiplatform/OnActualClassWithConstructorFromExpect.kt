// PLATFORM: Common
// FILE: Foo.kt
/**
 * @constructor Common Description
 */
expect class Foo()

// PLATFORM: Jvm
// FILE: Foo.kt
// MAIN
actual class Foo actual co<caret>nstructor()



//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">actual</span> <span style="color:#000080;font-weight:bold;">constructor</span> <span style="color:#000000;">Foo</span>()</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Common Description</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.ClassKotlin"/>&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a><br/></div>