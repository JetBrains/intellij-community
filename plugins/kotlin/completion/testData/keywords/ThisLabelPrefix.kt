class Outer {
    class Nested {
        inner class Inner {
            fun String.foo() {
                takeHandler1 {
                    takeHandler2({
                                     takeHandler3 { this@take<caret> }
                                 })
                }
            }
        }
    }
}

fun takeHandler1(handler: Int.() -> Unit){}
fun takeHandler2(handler: Char.() -> Unit){}
fun takeHandler3(handler: Char.() -> Unit){}

// INVOCATION_COUNT: 1
// ABSENT: "this"
// EXIST: { lookupString: "this@takeHandler3", itemText: "this", tailText: "@takeHandler3", typeText: "Char", attributes: "bold" }
// EXIST: { lookupString: "this@takeHandler2", itemText: "this", tailText: "@takeHandler2", typeText: "Char", attributes: "bold" }
// EXIST: { lookupString: "this@takeHandler1", itemText: "this", tailText: "@takeHandler1", typeText: "Int", attributes: "bold" }
// ABSENT: "this@foo"
// ABSENT: "this@Inner"
// ABSENT: "this@Nested"
// ABSENT: "this@Outer"
// NOTHING_ELSE
