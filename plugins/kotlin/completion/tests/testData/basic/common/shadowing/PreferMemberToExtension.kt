package ppp

class C {
    val xxx = ""
    fun xxx() = ""
    fun xxx(p: Int) = ""

    fun foo() {
        xx<caret>
    }
}

val C.xxx: Int
    get() = 1

fun C.xxx() = 1
fun C.xxx(p: Int) = 1
fun C.xxx(p: String) = 1

// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: null, typeText: "String", icon: "org/jetbrains/kotlin/idea/icons/field_value.svg"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "()", typeText: "String", icon: "nodes/method.svg"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "(p: Int)", typeText: "String", icon: "nodes/method.svg"}
// EXIST: { lookupString: "xxx", itemText: "xxx", tailText: "(p: String) for C in ppp", typeText: "Int", icon: "nodes/function.svg"}
// NOTHING_ELSE
