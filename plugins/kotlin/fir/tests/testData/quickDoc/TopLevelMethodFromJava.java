package server

import some.TopLevelMethodFromJava_DataKt

class Testing {
    void test() {
        TopLevelMethodFromJava_DataKt.<caret>foo(12);
    }
}

//INFO: <div class='definition'><pre>fun foo(bar: Int): Unit</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>KDoc foo</p></div><table class='sections'></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://some"><code><span style="color:#000000;">some</span></code></a><br/><icon src="file"/>&nbsp;TopLevelMethodFromJava_Data.kt<br/></div>
