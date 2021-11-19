// PROBLEM: none
// WITH_STDLIB

fun isAlphaOrBeta(str: String) = str.let<caret> { it == "Alpha" || it == "Beta" }
