// PSI_ELEMENT: com.intellij.psi.PsiClass
// OPTIONS: usages
import java.lang.System
fun f() {
    java.lang.System.out.println(off(1))
    java.lang.<caret>System.out.println(off(1))
}