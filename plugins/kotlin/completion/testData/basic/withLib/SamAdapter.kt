import lib.JavaClass

class KotlinClass : JavaClass()

fun test() = KotlinClass().<caret>

// IGNORE_K2
// EXIST: { lookupString: "execute", itemText: "execute", tailText: "(Runnable!, Int)", typeText: "Unit", attributes: "", icon: "Method"}
// EXIST: { lookupString: "execute", itemText: "execute", tailText: "((() -> Unit)!, Int)", typeText: "Unit", attributes: "", icon: "Method"}
