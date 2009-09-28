package org.jetbrains.plugins.groovy.dsl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.InheritanceUtil;
import org.codehaus.groovy.runtime.GroovyCategorySupport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
* @author peter
*/
class GroovyClassDescriptor implements ClassDescriptor {
  static {
    try {
      final AtomicInteger integer = GroovyCategorySupport.getCategoryNameUsage("aaa");
    }
    catch (NoSuchMethodError e) {
      throw new RuntimeException("Incompatible Groovy jar in classpath: " + GroovyCategorySupport.class.getResource("/") + ", please remove it");
    }
  }

  private final PsiClass myPsiClass;
  private final PsiElement myPlace;

  public GroovyClassDescriptor(@NotNull PsiClass psiClass, PsiElement place) {
    myPsiClass = psiClass;
    myPlace = place;
  }

  @Nullable
  public String getQualifiedName() {
    return myPsiClass.getQualifiedName();
  }

  public boolean isInheritor(String qname) {
    return InheritanceUtil.isInheritor(myPsiClass, qname);
  }

  public PsiElement getPlace() {
    return myPlace;
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
    return myPsiClass.hashCode() * 31 + myPlace.hashCode();
  }
}
