// "Replace with 'newFunction(x, { it * 2 })'" "true"

fun oldFunction(x: Int, action: (Int) -> Int): Int = action(x)

fun newFunction(x: Int, action: (Int) -> Int): Int = action(x)

fun exampleUsage() {
    val result = <caret>oldFunction(5, { it + 3 })
    println(result)
}
