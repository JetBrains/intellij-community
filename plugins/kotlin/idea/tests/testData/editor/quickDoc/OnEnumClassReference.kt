/**
 * Useless one
 */
enum class SomeEnum

fun use() {
    Some<caret>Enum::class
}

//K2_INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">enum</span> <span style="color:#000080;font-weight:bold;">class</span> <span style="color:#000000;">SomeEnum</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Useless one</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnEnumClassReference.kt<br/></div>
//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">final</span> <span style="color:#000080;font-weight:bold;">enum class</span> <span style="color:#000000;">SomeEnum</span></pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Useless one</p></div><table class='sections'></table><div class='bottom'><icon src="KotlinBaseResourcesIcons.Kotlin_file"/>&nbsp;OnEnumClassReference.kt<br/></div>
