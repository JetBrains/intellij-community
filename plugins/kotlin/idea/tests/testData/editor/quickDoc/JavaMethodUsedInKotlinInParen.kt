fun ktTestWithParen() {
    TestWithParen.foo("SomeTest", 1<caret>)
}

//INFO: <div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://TestWithParen"><code><span style="color:#000000;">TestWithParen</span></code></a></div><div class='definition'><pre><i><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.Contract"><code><span style="color:#808000;">Contract</span></code></a><span style="">(</span><span style="">value</span><span style=""> = </span><span style="color:#008000;font-weight:bold;">"_,&#32;_&#32;-&gt;&#32;new"</span><span style="">,&nbsp;</span><span style="">pure</span><span style=""> = </span><span style="color:#000080;font-weight:bold;">true</span><span style="">)</span></i><sup><font color="808080" size="3"><i>i</i></font></sup><a href="inferred.annotations"><icon src="AllIcons.Ide.External_link_arrow"></a>&nbsp;
//INFO: <i><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.NotNull"><code><span style="color:#808000;">NotNull</span></code></a></i><sup><font color="808080" size="3"><i>i</i></font></sup><a href="inferred.annotations"><icon src="AllIcons.Ide.External_link_arrow"></a>&nbsp;
//INFO: <span style="color:#000080;font-weight:bold;">public static</span>&nbsp;<a href="psi_element://java.lang.Object"><code><span style="color:#000000;">Object</span></code></a><span style="">[]</span>&nbsp;<span style="color:#000000;">foo</span><span style="">(</span><br>    <a href="psi_element://java.lang.String"><code><span style="color:#000000;">String</span></code></a>&nbsp;<span style="">str</span><span style="">,</span>
//INFO:     <span style="color:#000080;font-weight:bold;">int</span>&nbsp;<span style="">num</span><br><span style="">)</span></pre></div><div class='content'>
//INFO:   Java Method
//INFO:      </div><table class='sections'></table>
