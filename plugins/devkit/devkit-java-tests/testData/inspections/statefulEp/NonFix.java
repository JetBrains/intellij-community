import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.openapi.project.Project;

public class NonFix {
  final PsiElement pe;
  final PsiReference r;
  Project p;
  final Project pf;
  public NonFix() {
    super();
    pe = null;
    r = null;
    p = pf = null;
  }
  
  public static class Ext2 {
    
  }
}
