class KotlinClassUsedFromJava {
    void test() {
        KtJaEnumChild.getEntries<caret>();
    }
}

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">enum</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">KtJaEnumChild</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;KotlinEnumEntriesUsedFromJava_Data.kt<br/></div>

//INFO: <div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://KtJaEnumChild"><code><span style="color:#000000;">KtJaEnumChild</span></code></a></div><div class='definition'><pre><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.NotNull"><code><span style="color:#808000;">NotNull</span></code></a>&nbsp;
//INFO: <span style="color:#000080;font-weight:bold;">public static</span>&nbsp;<span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.NotNull"><code><span style="color:#808000;">NotNull</span></code></a>&nbsp;<a href="psi_element://kotlin.enums.EnumEntries"><code><span style="color:#000000;">kotlin.enums.EnumEntries</span></code></a><span style="">&lt;</span><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.NotNull"><code><span style="color:#808000;">NotNull</span></code></a>&nbsp;<a href="psi_element://KtJaEnumChild"><code><span style="color:#000000;">KtJaEnumChild</span></code></a><span style="">&gt;</span>&nbsp;<span style="color:#000000;">getEntries</span><span style="">(</span><span style="">)</span></pre></div><table class='sections'><p></table>
