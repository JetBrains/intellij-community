class Shadow {
    fun shade() {}
}

fun <X> Shadow.shade() {}

fun context() {
    Shadow().sha<caret>
}

// EXIST: { lookupString: "shade", itemText: "shade", tailText: "()", typeText: "Unit", icon: "nodes/method.svg"}
// EXIST: { lookupString: "shade", itemText: "shade", tailText: "() for Shadow in <root>", typeText: "Unit", icon: "nodes/function.svg"}
// NOTHING_ELSE