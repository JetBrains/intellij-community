package server

import some.TopLevelMethodFromJava_DataKt

class Testing {
    void test() {
        TopLevelMethodFromJava_DataKt.<caret>foo(12);
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">foo</span>(
//INFO:     <span style="color:#000000;">bar</span><span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Int">Int</a></span>
//INFO: )<span style="">: </span><span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>KDoc foo</p></div><table class='sections'></table><div class='bottom'><icon src="AllIcons.Nodes.Package"/>&nbsp;<a href="psi_element://some"><code><span style="color:#000000;">some</span></code></a><br/><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;TopLevelMethodFromJava_Data.kt<br/></div>
