
class Test {
    val outerVal: Int = 5
    data class Data (
        val innerVal: String
    ) {
        fun innerFun() {

        }
    }
}

fun main() {
    Test.Data::<caret>
}

// EXIST: innerVal
// EXIST: { "itemText": "innerFun", "tailText": "()" }
// ABSENT: outerVal