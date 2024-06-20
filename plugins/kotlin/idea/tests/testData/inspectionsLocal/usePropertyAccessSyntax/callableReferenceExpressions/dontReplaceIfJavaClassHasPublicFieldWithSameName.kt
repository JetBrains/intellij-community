// PROBLEM: none
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties
// IGNORE_K1
// For K1, it offers replacement but shouldn't. It's a bug, see KTIJ-21051
fun test(){
    doSth(ClassWithPublicFieldWithSameName::<caret>getValue)
}

fun doSth(foo: (ClassWithPublicFieldWithSameName) -> Int): Any = foo