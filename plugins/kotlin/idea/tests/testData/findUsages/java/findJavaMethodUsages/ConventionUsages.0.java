// PSI_ELEMENT: com.intellij.psi.PsiMethod
// OPTIONS: usages
public class Small {
    public boolean c<caret>ontains(String str) { // Call "Find usages" for this method
        return true;
    }
}

// IGNORE_FIR_LOG