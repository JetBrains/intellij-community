// PROBLEM: none
// WITH_STDLIB


val a = randomValue().let<caret> { r -> List(10) { r } }

fun randomValue(): Int = 42