class MyClass(val field: String)

fun action(block: () -> Unit) {}
fun actionWithReceiver(block: MyClass.() -> Unit) {}

val prop: Int
    get() {
        action<caret> {
            field
        }

        return 0
    }

// TYPE: WithReceiver

// It shouldn't be out-of-block as the backing field has more priority in both cases
// OUT_OF_BLOCK: false
