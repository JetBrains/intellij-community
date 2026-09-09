// COMPILER_ARGUMENTS: -Xcontext-parameters

class Box(val valueA: Any, val valueZ: Any)
class Holder(val box: Box)

context(holder: Holder)
fun test(): String {
    if (holder.box.valueZ is String) {
        return holder.box.value<caret>
    }
    return ""
}

// EXIST: { lookupString: "valueZ", typeText: "String" }
// ABSENT: valueA
