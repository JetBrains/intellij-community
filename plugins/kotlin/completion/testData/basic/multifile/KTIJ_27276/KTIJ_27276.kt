// IGNORE_K1
package bar

fun foo() {}

fun bar() {
    val myTestVariable = ""
    val f = if (my<caret>)
}

// WITH_ORDER
// EXIST: { lookupString: "myTestVariable", typeText: "String" }
// EXIST: { lookupString: "myTestVariable", typeText: "ERROR" }
// NOTHING_ELSE
