// FIR_COMPARISON
// FIR_IDENTICAL
fun test() {
    class Local {
        inner class InnerInLocal(i: Int) {
            constructor(i: Int, s: String) : this(i)
        }
    }

    Local().InnerInLocal<caret>
}

// EXIST: {"lookupString": "InnerInLocal", "tailText": "(i: Int)", "typeText":"Local.InnerInLocal", "icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg", "itemText":"InnerInLocal"}
// EXIST: {"lookupString": "InnerInLocal", "tailText": "(i: Int, s: String)", "typeText":"Local.InnerInLocal", "icon":"org/jetbrains/kotlin/idea/icons/classKotlin.svg", "itemText":"InnerInLocal"}
