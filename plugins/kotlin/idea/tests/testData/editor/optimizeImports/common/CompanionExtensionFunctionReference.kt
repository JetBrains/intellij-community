// FIR_COMPARISON
package test2

import foo.targetObjExt

fun check(target: foo.TargetObj) {
    val ref: () -> Unit = target::targetObjExt
}