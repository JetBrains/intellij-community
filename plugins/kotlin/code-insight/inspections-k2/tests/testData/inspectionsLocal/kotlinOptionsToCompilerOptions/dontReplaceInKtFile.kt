// PROBLEM: none

class Test{
    var parameter = 0
}

fun main() {
    val kotlinOptions = Test()
    <caret>kotlinOptions.parameter = 1
}