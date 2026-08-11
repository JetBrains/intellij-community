// AFTER_ERROR: Unresolved reference: list1
// AFTER_ERROR: Unresolved reference: list2
// AFTER_ERROR: Unresolved reference: newItem
// AFTER_ERROR: Unresolved reference: smth
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
fun test() {
    if (smth) list1 else {<caret>
        list2
    }.add(newItem)
}

