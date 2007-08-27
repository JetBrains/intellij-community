package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.PomField;
import com.intellij.psi.*;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GrFieldImpl extends GrVariableImpl implements GrField, PsiMetaOwner, PsiMetaData {
  public GrFieldImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitField(this);
  }

  public String toString() {
    return "Field";
  }

/*
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitField(this);
  }
*/

  public void setInitializer(@Nullable PsiExpression psiExpression) throws IncorrectOperationException {
  }

  public PomField getPom() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent().getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      return (PsiClass) parent.getParent();
    }

    return null;
  }

  public boolean isProperty() {
    final GrModifierList modifierList = getModifierList();
    return modifierList != null && !modifierList.hasExplicitVisibilityModifiers();
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getUseScope(this);
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place) {
    return false;
  }

  public PsiElement getDeclaration() {
    return this;
  }

  @NonNls
  public String getName(PsiElement context) {
    final String name = getName();
    if (isProperty()) {
      if (context  instanceof PsiMethodCallExpression) {
        final String refName = ((PsiMethodCallExpression) context).getMethodExpression().getReferenceName();
        if (name != null && refName != null) {
          if (refName.startsWith("get") || refName.startsWith("set")) {
            return refName.substring(0, 3) + StringUtil.capitalize(name);
          }
        }
      }
    }

    return name;
  }

  public void init(PsiElement element) {
  }

  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return this;
  }

  public boolean isMetaEnough() {
    return true;
  }
}
