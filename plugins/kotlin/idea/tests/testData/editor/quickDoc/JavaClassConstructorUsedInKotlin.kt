fun testing() {
    SomeClassWithParen("param", 1<caret>)
}

//INFO: <div class='definition'><pre><i><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.Contract"><code><span style="color:#808000;">Contract</span></code></a><span style="">(</span><span style="">pure</span><span style=""> = </span><span style="color:#000080;font-weight:bold;">true</span><span style="">)</span></i><sup><font color="808080" size="3"><i>i</i></font></sup><a href="inferred.annotations"><icon src="AllIcons.Ide.External_link_arrow"></a>&nbsp;
//INFO: <span style="color:#000080;font-weight:bold;">public</span>&nbsp;<span style="color:#000000;">SomeClassWithParen</span><span style="">(</span><br>    <a href="psi_element://java.lang.String"><code><span style="color:#000000;">String</span></code></a>&nbsp;<span style="">str</span><span style="">,</span>
//INFO:     <span style="color:#000080;font-weight:bold;">int</span>&nbsp;<span style="">num</span><br><span style="">)</span></pre></div><table class='sections'><p></table><div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://SomeClassWithParen"><code><span style="color:#000000;">SomeClassWithParen</span></code></a></div>
