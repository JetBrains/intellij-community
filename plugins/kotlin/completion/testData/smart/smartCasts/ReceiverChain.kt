class Box(val value: Any)
class Holder(val box: Box)

fun test(holder: Holder): String {
    if (holder.box.value is String) {
        return holder.box.val<caret>
    }
    return ""
}

// EXIST: { lookupString: "value", typeText: "String" }
