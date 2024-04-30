// PROBLEM: none
// FIR_COMPARISON
fun foo(arg: Any) = if (arg !is String?<caret>) null else arg?.length