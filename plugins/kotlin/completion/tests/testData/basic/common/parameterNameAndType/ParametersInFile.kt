import java.io.*

class X {
    fun f1(strings: List<String>) { }
    fun f2(numbers: List<Int>) { }
}

fun f3(strings: List<String>) { }
fun f4(value: Any?) { }
fun f5(value: File) { }
fun f6(handler: (() -> String)?) { }

class C(val handler: () -> Unit) {
    companion object {
        fun foo(<caret>) {
            fun local(localParam: String/* it should not be included by performance reasons*/){}
        }
    }
}

// EXIST: { lookupString: "strings: List", itemText: "strings: List<String>", tailText: " (kotlin.collections)", icon: "org/jetbrains/kotlin/idea/icons/interfaceKotlin.svg"}
// EXIST: { lookupString: "numbers: List", itemText: "numbers: List<Int>", tailText: " (kotlin.collections)", icon: "org/jetbrains/kotlin/idea/icons/interfaceKotlin.svg"}
// EXIST: { lookupString: "value: Any", itemText: "value: Any?", tailText: " (kotlin)", icon: "org/jetbrains/kotlin/idea/icons/classKotlin.svg"}
// EXIST_JAVA_ONLY: { lookupString: "value: File", itemText: "value: File", tailText: " (java.io)" }
// EXIST: { lookupString: "handler: (() -> String)?", itemText: "handler: (() -> String)?", tailText: null, icon: "org/jetbrains/kotlin/idea/icons/lambda.svg"}
// EXIST: { lookupString: "handler: () -> Unit", itemText: "handler: () -> Unit", tailText: null, icon: "org/jetbrains/kotlin/idea/icons/lambda.svg"}
// ABSENT: { itemText: "localParam: String" }
// ABSENT: { itemText: "file: File" }
