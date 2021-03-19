// IGNORE_BROKEN_LC: it is known broken case for old LightClasses
// this test data is used for ULC as well that has to pass

// a.b.c.ActualTypeAliasCustomJvmPackageNameKt
// WITH_RUNTIME
@file:JvmPackageName("a.b.c")
package p

actual typealias B = List<Int>