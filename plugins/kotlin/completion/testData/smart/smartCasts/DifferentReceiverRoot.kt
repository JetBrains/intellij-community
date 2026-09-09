class Box(val value: Any, val valueText: String)
class Holder(val box: Box)

fun test(first: Holder, second: Holder): String {
    if (first.box.value is String) {
        return second.box.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
