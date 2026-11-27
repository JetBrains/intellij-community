import kotlin.reflect.KClass

enum class Choice { `when` }
class Outer {
    annotation class Nested(val name: String = "")
}
annotation class Tag(val empty: Outer.Nested, val `when`: Outer.Nested, val type: KClass<*>, val choice: Choice)
@Target(AnnotationTarget.PROPERTY_GETTER)
annotation class Read

@Tag(Outer.Nested(), Outer.Nested("<&>"), String::class, Choice.`when`)
data class Exa<caret>mple<T>(@get:Read val first: T, val second: String = "<&>")

//INFO: <div class='definition'><pre><span style="color:#808000;">@</span><span style="color:#808000;"><a href="psi_element://Tag">Tag</a></span><span style="">(</span><span style="color:#000000;">empty</span> <span style="">=</span> <span style="color:#000000;"><a href="psi_element://Outer">Outer</a></span><span style="">.</span><span style="color:#808000;"><a href="psi_element://Outer.Nested">Nested</a></span><span style="">()</span><span style="">,</span> <span style="color:#000000;">`when`</span> <span style="">=</span> <span style="color:#000000;"><a href="psi_element://Outer">Outer</a></span><span style="">.</span><span style="color:#808000;"><a href="psi_element://Outer.Nested">Nested</a></span><span style="">(</span><span style="color:#000000;">name</span> <span style="">=</span> <span style="color:#008000;font-weight:bold;">"&lt;&amp;&gt;"</span><span style="">)</span><span style="">,</span> <span style="color:#000000;">type</span> <span style="">=</span> <span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span><span style="">::</span><span style="color:#000080;font-weight:bold;">class</span><span style="">,</span> <span style="color:#000000;">choice</span> <span style="">=</span> <span style="color:#000000;"><a href="psi_element://Choice">Choice</a></span><span style="">.</span><span style="color:#000000;">`when`</span><span style="">)</span>
//INFO: <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">data</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">Example</span><span style="">&lt;</span><span style="color:#20999d;">T</span><span style="">&gt;</span><span style="">(</span>
//INFO:     <span style="color:#808000;">@</span><span style="color:#000080;font-weight:bold;">get</span><span style="">:</span><span style="color:#808000;"><a href="psi_element://Read">Read</a></span> <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">first</span><span style="">:</span> <span style="color:#20999d;"><a href="psi_element://Example.T">T</a></span><span style="">,</span>
//INFO:     <span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">val</span> <span style="color:#660e7a;font-weight:bold;">second</span><span style="">:</span> <span style="color:#000000;"><a href="psi_element://kotlin.String">String</a></span> <span style="">=</span> <span style="color:#008000;font-weight:bold;">"&lt;&amp;&gt;"</span>
//INFO: <span style="">)</span></pre></div><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;GenericDataClass.kt<br/></div>
