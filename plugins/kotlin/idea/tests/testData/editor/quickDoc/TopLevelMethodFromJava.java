package server

import some.TopLevelMethodFromJava_DataKt

class Testing {
    void test() {
        TopLevelMethodFromJava_DataKt.<caret>foo(12);
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">foo</span>(
//INFO:     <span style="color:#000000;">bar</span><span style="">: </span><span style="color:#000000;">Int</span>
//INFO: )<span style="">: </span><span style="color:#000000;">Unit</span></pre></div><div class='content'><p>KDoc foo</p></div><table class='sections'></table>
