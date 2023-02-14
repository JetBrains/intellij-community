// "Create expected class in common module testModule_Common" "true"
// SHOULD_FAIL_WITH: Some types are not accessible from testModule_Common:
// SHOULD_FAIL_WITH: JvmAnnotationClass

package one.two

annotation class JvmAnnotationClass

@JvmAnnotationClass
actual class Plat<caret>form