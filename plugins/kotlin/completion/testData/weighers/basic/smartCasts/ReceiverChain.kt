class Box(val valueA: Any, val valueZ: Any)
class Holder(val box: Box)

fun test(holder: Holder): String {
    if (holder.box.valueZ is String) {
        return ((holder).box).value<caret>
    }
    return ""
}

// ORDER: valueZ
// ORDER: valueA
