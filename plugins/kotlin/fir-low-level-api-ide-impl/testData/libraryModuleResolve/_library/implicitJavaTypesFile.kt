package implicitJavaTypes

// this file contains kotlin declarations with implicit types inferred from java declarations

var listOfStrings = java.util.Arrays.asList("hello")

var string = listOfStrings.get(0)

fun stringFun() = string
