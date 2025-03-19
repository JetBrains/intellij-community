// ALLOW_ERRORS
val fgs = MyDependency(classifier = "")
val dva = fgs.copy(classifier = "")
val dva2 = fgs.copy(<!HIGHLIGHTING("severity='ERROR'; descr='[NAMED_PARAMETER_NOT_FOUND] No parameter with name 'abc' found.'")!>abc<!> = "")

// IGNORE_PLATFORM_JS: KTIJ-29719
// IGNORE_PLATFORM_NATIVE: KTIJ-29719
// IGNORE_PLATFORM_COMMON_NATIVE+JVM: KTIJ-29719
