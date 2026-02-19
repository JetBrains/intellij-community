// "Replace annotation with kotlin.annotation.Retention" "true"

import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Retention

@Retention<caret>(RetentionPolicy.RUNTIME)
annotation class Foo
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.DeprecatedJavaAnnotationFix