// FIR_COMPARISON
// FIR_IDENTICAL
/* Number of result in completion should be fixed after removing duplicates */
package first

fun firstFun() {
  val a = KProp<caret>
}

// INVOCATION_COUNT: 1
// EXIST: { lookupString:"KProperty", itemText:"KProperty", tailText:"<V> (kotlin.reflect)", icon: "org/jetbrains/kotlin/idea/icons/interfaceKotlin.svg"}
