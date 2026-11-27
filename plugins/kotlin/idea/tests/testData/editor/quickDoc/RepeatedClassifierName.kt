class Same {
    class Same
}
fun exa<caret>mple(): Same.Same = Same.Same()

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">example</span><span style="">(</span><span style="">)</span><span style="">:</span> <span style="color:#000000;"><a href="psi_element://Same">Same</a></span><span style="">.</span><span style="color:#000000;"><a href="psi_element://Same.Same">Same</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;RepeatedClassifierName.kt<br/></div>
