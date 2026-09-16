class Node(val value: Any, val valueText: String, val next: Node)

fun test(node: Node): String {
    if (node.value is String) {
        return node.next.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
