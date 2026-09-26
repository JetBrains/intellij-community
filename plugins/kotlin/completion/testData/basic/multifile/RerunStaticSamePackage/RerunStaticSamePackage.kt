package a
fun test() {
    somePrefix<caret>
}

// EXIST: somePrefixJavaStaticFieldA
// EXIST: somePrefixJavaStaticMethodA
// EXIST: somePrefixJavaStaticFieldC
// EXIST: somePrefixJavaStaticMethodC

// EXIST: somePrefixKotlinStaticFieldA
// EXIST: somePrefixKotlinStaticFieldB
// EXIST: somePrefixKotlinStaticMethodA
// EXIST: somePrefixKotlinStaticMethodB

// NOTHING_ELSE

// INVOCATION_COUNT: 1