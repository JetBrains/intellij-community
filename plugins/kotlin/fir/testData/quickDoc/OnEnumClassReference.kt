/**
 * Useless one
 */
enum class SomeEnum

fun use() {
    Some<caret>Enum::class
}

//INFO: <div class='definition'><pre>enum class SomeEnum</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Useless one</p></div><table class='sections'></table><div class='bottom'><icon src="file"/>&nbsp;OnEnumClassReference.kt<br/></div>
