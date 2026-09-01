// "Replace invalid positioned arguments for annotation" "true"
// WITH_STDLIB
// ERROR: Only named arguments are available for Java annotations
// ERROR: Only named arguments are available for Java annotations
// COMPILER_ARGUMENTS: -XXLanguage:-EnforceNamedArgumentsOnJavaAnnotationInAccessors

@get:Ann(1, /*abc*/arg1 = "abc", arg2 = arrayOf(Int::class, Array<Int>::class)<selection><caret></selection>, arg3 = String::class)
val prop = ""
