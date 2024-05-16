// FIX: Replace 'if' expression with safe access expression
/* In K2 nullable checks are also subject to the inspection */
fun foo(arg: Any?) = if (arg !is String?<caret>) null else arg?.length