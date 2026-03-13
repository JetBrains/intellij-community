fun testJavaRecordStaticMethod() {
    somePrefix<caret>
}

// EXIST: somePrefixJavaStaticField
// EXIST: somePrefixJavaStaticMethod
// EXIST: somePrefixKotlinStaticField
// EXIST: somePrefixKotlinStaticMethod
// ABSENT: somePrefixJavaInstanceFieldA
// ABSENT: somePrefixJavaInstanceFieldB
// ABSENT: somePrefixJavaInstanceMethod
// ABSENT: somePrefixKotlinInstanceFieldA
// ABSENT: somePrefixKotlinInstanceFieldB
// ABSENT: somePrefixKotlinInstanceMethod

// INVOCATION_COUNT: 1
// IGNORE_K1