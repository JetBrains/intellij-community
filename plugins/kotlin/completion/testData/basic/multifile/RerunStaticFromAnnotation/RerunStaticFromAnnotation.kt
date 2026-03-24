fun testJavaAnnotationConst() {
    somePrefix<caret>
}

// EXIST: somePrefixJavaStaticField
// EXIST: somePrefixKotlinStaticField
// EXIST: somePrefixKotlinStaticMethod
// ABSENT: somePrefixJavaInstanceMethod
// ABSENT: somePrefixKotlinInstanceField
// ABSENT: somePrefixKotlinInstanceMethod

// INVOCATION_COUNT: 1