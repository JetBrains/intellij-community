fun testing() {
    SomeClassWithParen("param", 1<caret>)
}

//INFO: <div class='definition'><pre><i><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.Contract"><code><span style="color:#808000;">Contract</span></code></a><span style="">(</span><span style="">pure</span><span style=""> = </span><span style="color:#000080;font-weight:bold;">true</span><span style="">)</span></i>&nbsp;
//INFO: <span style="color:#000080;font-weight:bold;">public</span>&nbsp;<span style="color:#000000;">SomeClassWithParen</span><span style="">(</span><br>    <a href="psi_element://java.lang.String"><code><span style="color:#000000;">String</span></code></a>&nbsp;<span style="">str</span><span style="">,</span>
//INFO:     <span style="color:#000080;font-weight:bold;">int</span>&nbsp;<span style="">num</span><br><span style="">)</span></pre></div><table class='sections'><p><tr><td valign='top' class='section'><p><i>Inferred</i><br> annotations:</td><td valign='top'><p><i><span style="color:#808000;">@</span><a href="psi_element://org.jetbrains.annotations.Contract"><span style="color:#808000;">org.jetbrains.annotations.Contract</span></a><span style="">(</span><span style="">pure</span><span style=""> = </span><span style="color:#000080;font-weight:bold;">true</span><span style="">)</span></i></td></table>
