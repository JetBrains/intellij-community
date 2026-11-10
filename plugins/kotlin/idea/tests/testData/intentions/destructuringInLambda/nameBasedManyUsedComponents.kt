// WITH_STDLIB
// HIGHLIGHT: INFORMATION
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

data class MyDataClass(
    val argument1: String,
    val argument2: String,
    val argument3: String,
    val argument4: String,
    val argument5: String,
    val argument6: String,
)

fun myFun(element: MyDataClass) {
    listOf(element).single().let {<caret> println(it.argument1 + it.argument2 + it.argument3 + it.argument4 + it.argument5 + it.argument6) }
}
