class Test private constructor() {

}

fun test(): Test = Tes<caret>

// ABSENT: {"lookupString":"Test", "tailText":"() (<root>)"}

