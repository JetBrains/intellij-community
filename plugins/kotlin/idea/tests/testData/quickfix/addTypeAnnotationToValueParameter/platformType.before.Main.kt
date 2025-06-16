// "Add type 'Any' to parameter 'param'" "true"
// WITH_STDLIB
fun test(
    param = JavaClass.foo()<caret>
): Unit = Unit

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddTypeAnnotationToValueParameterFixFactory$AddTypeAnnotationToValueParameterFix