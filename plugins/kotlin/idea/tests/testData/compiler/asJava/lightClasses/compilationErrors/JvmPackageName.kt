// a.b.c.JvmPackageNameKt

@file:JvmPackageName("a.b.c")
package p

fun f() {

}

// WITH_RUNTIME

// IGNORE_BROKEN_LC: it is known broken case for old LightClasses
// this test data is used for ULC as well that has to pass