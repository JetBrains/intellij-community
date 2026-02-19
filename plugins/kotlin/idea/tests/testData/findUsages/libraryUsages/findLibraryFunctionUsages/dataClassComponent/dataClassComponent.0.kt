// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
package usages

import library.KotlinDataClass
fun test(k: KotlinDataClass) {
    k.component1<caret>()
}