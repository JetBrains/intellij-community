fun testing() {
    <caret>SomeClass<List<String>>()
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">SomeClass</span><span style="">&lt;</span><span style="color:#20999d;">T</span><span style="color:#000080;font-weight:bold;"> extends </span><a href="psi_element://java.util.List"><code><span style="color:#000000;">List</span></code></a><span style="">&gt;</span></pre></div><div class='content'>
//INFO:   Some Java Class
//INFO:   </div><table class='sections'><p><tr><td valign='top' class='section'><p>Type parameters:</td><td valign='top'><code>&lt;<span style="color:#20999d;">T</span>&gt;</code></td></table>
