fun testJavaEnumStatic() {
    somePrefix<caret>
}

// EXIST: somePrefixJavaStaticField
// EXIST: somePrefixJavaStaticMethod
// EXIST: somePrefixKotlinStaticField
// EXIST: somePrefixKotlinStaticMethod
// ABSENT: somePrefixJavaInstanceField
// ABSENT: somePrefixJavaInstanceMethod
// ABSENT: somePrefixKotlinInstanceField
// ABSENT: somePrefixKotlinInstanceMethod

// INVOCATION_COUNT: 1