// FIR_COMPARISON
package first

fun firstFun() {
    val a = ""
    a.<caret>
}

// EXIST: helloFun
// EXIST: helloWithParams
// EXIST: helloFunGeneric
// ABSENT: helloDynamic
// ABSENT: helloFake
// IGNORE_K1
// Note: helloDynamic() is anyway not allowed to be called