abstract class Test(a: Int) {

}

class Impl: Test(5)

fun test(): Test = Tes<caret>

// ABSENT: {"lookupString":"Test", "tailText":"(a: Int) (<root>)"}

