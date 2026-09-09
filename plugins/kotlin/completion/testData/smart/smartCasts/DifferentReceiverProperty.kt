class Box(val value: Any, val valueText: String)
class Holder(val first: Box, val second: Box)

fun test(holder: Holder): String {
    if (holder.first.value is String) {
        return holder.second.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueText", typeText: "String" }
// ABSENT: value
