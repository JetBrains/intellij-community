// IS_APPLICABLE: false
// ERROR: Overload resolution ambiguity: <br>public val test: Int defined in one.two.three in file TopLevelPropertyAndFunction.kt<br>public fun test(): Unit defined in one.two.three in file TopLevelPropertyAndFunction.kt

package one.two.three

val test: Int = 42

fun test() {
    ::<caret>test
}
