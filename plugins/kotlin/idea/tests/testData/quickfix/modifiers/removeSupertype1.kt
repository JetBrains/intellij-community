// "Remove supertype" "true"
// K2_ERROR: MANY_CLASSES_IN_SUPERTYPE_LIST
open class C1 {}
open class C2 {}
class C3: C1(), C2<caret>() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveSupertypeFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveSupertypeFix