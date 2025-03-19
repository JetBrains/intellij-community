package pack

fun usage() {
    "".topLevelExtensionF<caret>
}

// ELEMENT: topLevelExtensionFunction
// TAIL_TEXT: "() for String in <root>"