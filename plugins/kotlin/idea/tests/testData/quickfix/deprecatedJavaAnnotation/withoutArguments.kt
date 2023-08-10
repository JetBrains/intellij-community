// "Replace annotation with kotlin.annotation.MustBeDocumented" "true"

import java.lang.annotation.Documented

@Documented<caret>
annotation class Foo
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.DeprecatedJavaAnnotationFix