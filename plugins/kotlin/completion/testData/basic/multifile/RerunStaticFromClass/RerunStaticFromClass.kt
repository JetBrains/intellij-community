fun testJavaClassStatic() {
    somePrefix<caret>
}

// EXIST: somePrefixJavaStaticField
// EXIST: somePrefixJavaStaticMethod
// EXIST: somePrefixKotlinStaticField
// EXIST: somePrefixKotlinStaticMethod
// ABSENT: somePrefixJavaInstanceField
// ABSENT: somePrefixJavaInstanceMethod
// ABSENT: somePrefixKotlinInstanceFieldA
// ABSENT: somePrefixKotlinInstanceFieldB
// ABSENT: somePrefixKotlinInstanceMethod

// INVOCATION_COUNT: 1