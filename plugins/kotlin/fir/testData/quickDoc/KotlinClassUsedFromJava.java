import testing.Test;

class KotlinClassUsedFromJava {
    void test() {
        <caret>Test();
    }
}

//INFO: <div class='definition'><pre>class Test</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some comment</p></div><table class='sections'></table><div class='bottom'><icon src="class"/>&nbsp;<a href="psi_element://testing"><code><span style="color:#000000;">testing</span></code></a><br/><icon src="file"/>&nbsp;KotlinClassUsedFromJava_Data.kt<br/></div>
