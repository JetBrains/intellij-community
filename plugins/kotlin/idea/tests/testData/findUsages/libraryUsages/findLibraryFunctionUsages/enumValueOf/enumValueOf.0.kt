// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages
package usages

import library.MyEnum
fun test() {
    MyEnum.valueOf<caret>("A")
}