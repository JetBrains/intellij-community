// IGNORE_K1
abstract class A<T> {
    abstract fun func(t: T): T

    companion object {
        fun funcFromCompanion() {}
    }
}

fun test() {
    val p = A<String>::func<caret>
}

// EXIST: { lookupString: "func", tailText: "(t: String)", typeText: "String", attributes: "bold"}
// EXIST: { lookupString: "funcFromCompanion", tailText: "()", typeText: "Unit", attributes: "bold"}
