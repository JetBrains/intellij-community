// IGNORE_K1
abstract class A<U, V, W> {
    abstract fun func(u: U, v: V): W

    companion object {
        fun funcFromCompanion() {}
    }
}

fun test() {
    val p = A<String, ErrorType>::func<caret>
}

// EXIST: { lookupString: "func", tailText: "(u: String, v: V)", typeText: "W", attributes: "bold"}
// EXIST: { lookupString: "funcFromCompanion", tailText: "()", typeText: "Unit", attributes: "bold"}
