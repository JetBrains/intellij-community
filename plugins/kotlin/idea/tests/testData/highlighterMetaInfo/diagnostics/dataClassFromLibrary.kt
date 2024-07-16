// ALLOW_ERRORS
val fgs = MyDependency(classifier = "")
val dva = fgs.copy(classifier = "")
val dva2 = fgs.copy(abc = "")

// IGNORE_PLATFORM_JS: KTIJ-29719
// IGNORE_PLATFORM_NATIVE: KTIJ-29719
// IGNORE_PLATFORM_COMMON_NATIVE+JVM: KTIJ-29719
