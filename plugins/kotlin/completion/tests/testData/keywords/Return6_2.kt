fun foo() {
    takeMyHandler1 {
        takeHandler2 {
            takeMyHandler3({ return@takeMy<caret> })
        }
    }
}

inline fun takeMyHandler1(handler: () -> Unit){}
inline fun takeHandler2(handler: () -> Unit){}

inline fun takeMyHandler3(handler: () -> Unit){}

// INVOCATION_COUNT: 1
// EXIST: { lookupString: "return@takeMyHandler1", itemText: "return", tailText: "@takeMyHandler1", attributes: "bold" }
// ABSENT: "return@takeHandler2"
// EXIST: { lookupString: "return@takeMyHandler3", itemText: "return", tailText: "@takeMyHandler3", attributes: "bold" }
// NOTHING_ELSE
