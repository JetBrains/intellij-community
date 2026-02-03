package test

class User(val name: String)

fun getName(): String {

}

fun test(someName: String) {
    val aVariable = ""
    val yVariable = ""
    val name = ""
    val bVariable = ""
    val zVariable = ""
    User(<caret>)
}

// ORDER: name
// ORDER: someName
// ORDER: getName
// ORDER: aVariable
// ORDER: bVariable
// ORDER: yVariable
// ORDER: zVariable
