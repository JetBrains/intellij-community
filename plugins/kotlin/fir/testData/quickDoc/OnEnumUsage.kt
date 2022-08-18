/**
 * Enum of 1, 2
 */
enum class SomeEnum(val i: Int) {
    One(1), Two(2);
}

fun use() {
    Some<caret>Enum.One
}

//INFO: <div class='definition'><pre>enum class SomeEnum</pre></div><div class='content'><p style='margin-top:0;padding-top:0;'>Enum of 1, 2</p></div><table class='sections'></table><div class='bottom'><icon src="file"/>&nbsp;OnEnumUsage.kt<br/></div>
