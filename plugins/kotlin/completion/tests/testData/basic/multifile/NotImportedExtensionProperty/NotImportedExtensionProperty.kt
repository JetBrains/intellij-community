// FIR_COMPARISON
package first

fun firstFun() {
    val a = ""
    a.hello<caret>
}

// EXIST: { lookupString: "helloProp1", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "helloProp2", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// ABSENT: helloProp3
// ABSENT: helloProp4
// ABSENT: helloPropPrivate
// NOTHING_ELSE
