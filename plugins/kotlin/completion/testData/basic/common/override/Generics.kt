// FIR_COMPARISON
// FIR_IDENTICAL
interface I<T> {
    fun foo(t: T): T
}

class A<T> : List<String>, I<T> {
    o<caret>
}

// EXIST: { lookupString: "override", itemText: "override"}
// EXIST: { itemText: "override fun hashCode(): Int {...}", tailText: null, typeText: "Any", attributes: "", icon: "Method"}
// EXIST: { itemText: "override fun foo(t: T): T {...}", tailText: null, typeText: "I", attributes: "bold", icon: "nodes/abstractMethod.svg"}
// EXIST: { itemText: "override fun get(index: Int): String {...}", tailText: null, typeText: "List", attributes: "bold", icon: "nodes/abstractMethod.svg"}
