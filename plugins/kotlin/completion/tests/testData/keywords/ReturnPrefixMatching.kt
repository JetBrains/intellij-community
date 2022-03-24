fun turnLeft() {}

fun foo() {
    takeHandler1 {
        takeHandler2({ tu<caret> })
    }
}

inline fun takeHandler1(handler: () -> Unit){}
inline fun takeHandler2(handler: () -> Unit){}

// NUMBER: 0