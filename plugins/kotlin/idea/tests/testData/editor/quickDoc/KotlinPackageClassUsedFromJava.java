import testing.KotlinPackageClassUsedFromJava_DataKt;

class KotlinPackageClassUsedFromJava {
    void test() {
        <caret>KotlinPackageClassUsedFromJava_DataKt.foo();
    }
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">KotlinPackageClassUsedFromJava_DataKt</span>
//INFO: <span style="color:#000080;font-weight:bold;">extends </span><a href="psi_element://java.lang.Object"><code><span style="color:#000000;">Object</span></code></a><br><span class='grayed'>defined in </span><a href="psi_element://testing"><code><span style="color:#000000;">testing</span></code></a></pre></div><table class='sections'></table>
