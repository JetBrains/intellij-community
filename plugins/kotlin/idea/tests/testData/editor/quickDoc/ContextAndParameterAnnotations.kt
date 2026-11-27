annotation class Tag
@Tag
context(value: String)
fun <@Tag T> exa<caret>mple(@Tag arg: T) {}

//INFO: <div class='definition'><pre><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Tag">Tag</a></span>
//INFO: <span style="color:#000080;font-weight:bold;">context</span><span style="">(</span><span style="color:#000000;">value</span><span style="">:</span> <span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span><span style="">)</span>
//INFO: <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="">&lt;</span><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Tag">Tag</a></span> <span style="color:#20999d;">T</span><span style="">&gt;</span> <span style="color:#000000;">example</span><span style="">(</span><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Tag">Tag</a></span> <span style="color:#000000;">arg</span><span style="">:</span> <span style="color:#20999d;"><a href="psi_element://example.T">T</a></span><span style="">)</span><span style="">:</span> <span style="color:#000000;"><a href="psi_element://kotlin.Unit">Unit</a></span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;ContextAndParameterAnnotations.kt<br/></div>
