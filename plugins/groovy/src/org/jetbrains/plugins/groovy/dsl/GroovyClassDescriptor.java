package org.jetbrains.plugins.groovy.dsl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author peter
*/
class GroovyClassDescriptor implements ClassDescriptor {
  private final PsiClass myPsiClass;

  public GroovyClassDescriptor(@NotNull PsiClass psiClass) {
    myPsiClass = psiClass;
  }

  @Nullable
  public String getQualifiedName() {
    return myPsiClass.getQualifiedName();
  }

  public boolean isInheritor(String qname) {
    return InheritanceUtil.isInheritor(myPsiClass, qname);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GroovyClassDescriptor that = (GroovyClassDescriptor)o;

    if (!myPsiClass.equals(that.myPsiClass)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myPsiClass.hashCode();
  }
}
