public interface Test {
    public fun String.extension()

    public companion object : Test by TestImpl
}

internal object TestImpl : Test {
    override fun String.extension() = TODO()
}

fun usage() {
    "".ext<caret>
}

// EXIST: { lookupString: "extension", tailText: "() for String in TestImpl (<root>)", itemText: "extension", icon: "nodes/function.svg"}
// EXIST: { lookupString: "extension", tailText: "() for String in Test.Companion (<root>)", itemText: "extension", icon: "nodes/function.svg"}
