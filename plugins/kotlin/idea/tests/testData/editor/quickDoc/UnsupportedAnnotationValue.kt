annotation class Tag(val name: String)
fun value() = ""
@Tag(value())
class ER<caret>ROR

//INFO: <div class='definition'><pre><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Tag">Tag</a></span><span style="">(</span><span style="color:#000000;">name</span> <span style="">=</span> <span style="color:#000000;">ERROR</span><span style="">)</span>
//INFO: <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">ERROR</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;UnsupportedAnnotationValue.kt<br/></div>
