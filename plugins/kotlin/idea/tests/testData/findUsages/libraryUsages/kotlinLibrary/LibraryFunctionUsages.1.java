// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: ""
// FIND_BY_REF
// WITH_FILE_NAME
package usages

import library.*

class J {
    static void test() {
        LibraryPackage.foo();
    }
}