import testing.KotlinPackageClassUsedFromJava_DataKt;

class KotlinPackageClassUsedFromJava {
    void test() {
        <caret>KotlinPackageClassUsedFromJava_DataKt.foo();
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">KotlinPackageClassUsedFromJava_DataKt</span></pre></div><table class='sections'></table>
