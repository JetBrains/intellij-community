// AFTER_ERROR: Unresolved reference: list1
// AFTER_ERROR: Unresolved reference: list2
// AFTER_ERROR: Unresolved reference: newItem
// AFTER_ERROR: Unresolved reference: smth
// K2_AFTER_ERROR: Unresolved reference 'add'.
// K2_AFTER_ERROR: Unresolved reference 'list1'.
// K2_AFTER_ERROR: Unresolved reference 'list2'.
// K2_AFTER_ERROR: Unresolved reference 'newItem'.
// K2_AFTER_ERROR: Unresolved reference 'smth'.
fun test() {
    if (smth) list1 else {<caret>
        list2
    }.add(newItem)
}

