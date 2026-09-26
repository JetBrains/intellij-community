fun test() {
    somePrefix<caret>
}

// EXIST: somePrefixJavaStaticFieldA
// EXIST: somePrefixJavaStaticMethodA
// EXIST: somePrefixJavaStaticFieldB
// EXIST: somePrefixJavaStaticFieldC
// EXIST: somePrefixJavaStaticMethodB
// EXIST: somePrefixKotlinStaticField
// EXIST: somePrefixKotlinStaticMethod

// NOTHING_ELSE

// INVOCATION_COUNT: 1