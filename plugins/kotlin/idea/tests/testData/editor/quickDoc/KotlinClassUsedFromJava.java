import testing.Test;

class KotlinClassUsedFromJava {
    void test() {
        <caret>Test();
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">Test</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Some comment</p></div><table class='sections'></table><div class='bottom'><icon src="/org/jetbrains/kotlin/idea/icons/classKotlin.svg"/>&nbsp;<a href="psi_element://testing"><code><span style="color:#000000;">testing</span></code></a><br/><icon src="/org/jetbrains/kotlin/idea/icons/kotlin_file.svg"/>&nbsp;KotlinClassUsedFromJava_Data.kt<br/></div>
