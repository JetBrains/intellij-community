package server

import some.TopLevelMethodFromJava_DataKt

class Testing {
    void test() {
        TopLevelMethodFromJava_DataKt.<caret>foo(12);
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">foo</span>(
//INFO:     <span style="color:#000000;">bar</span><span style="">: </span><span style="color:#000000;">Int</span>
//INFO: )<span style="">: </span><span style="color:#000000;">Unit</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>KDoc foo</p></div><table class='sections'></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/classKotlin.svg"/>&nbsp;<a href="psi_element://some"><code><span style="color:#000000;">some</span></code></a><br/><icon src="/org/jetbrains/kotlin/idea/icons/kotlin_file.svg"/>&nbsp;TopLevelMethodFromJava_Data.kt<br/></div>
