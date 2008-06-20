/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GrFieldImpl extends GrVariableImpl implements GrField {
  private GrAccessorMethod mySetter;
  private GrAccessorMethod[] myGetters;

  private boolean mySetterInitialized = false;
  private boolean myGettersInitialized = false;

  public GrFieldImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitField(this);
  }

  public String toString() {
    return "Field";
  }

  public void setInitializer(@Nullable PsiExpression psiExpression) throws IncorrectOperationException {
  }

  public boolean isDeprecated() {
    return false;
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent().getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass) pparent;
      }
    }

    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFileBase) {
      return ((GroovyFileBase) file).getScriptClass();
    }

    assert false;
    return null;
  }

  public boolean isProperty() {
    final PsiClass clazz = getContainingClass();
    if (clazz.isInterface()) return false;
    final GrModifierList modifierList = getModifierList();
    return modifierList != null && !modifierList.hasExplicitVisibilityModifiers();
  }

  public GrAccessorMethod getSetter() {
    if (mySetterInitialized) return mySetter;

    mySetter = null;

    if (isProperty() && !hasModifierProperty(PsiModifier.FINAL)) {
      String name = getName();
      if (name != null) {
        name = "set" + StringUtil.capitalize(name);
        final GrAccessorMethod setter = new GrAccessorMethodImpl(this, true, name);
        final PsiClass clazz = getContainingClass();
        if (!hasContradictingMethods(setter, clazz)) {
          mySetter = setter;
        }
      }

    }

    mySetterInitialized = true;
    return mySetter;
  }

  public void clearCaches() {
    mySetterInitialized = myGettersInitialized = false;
  }

  @NotNull
  public GrAccessorMethod[] getGetters() {
    if (myGettersInitialized) return myGetters;

    myGetters = GrAccessorMethod.EMPTY_ARRAY;

    if (isProperty()) {
      String name = getName();
      if (name != null) {
        final PsiClass clazz = getContainingClass();
        name = StringUtil.capitalize(name);
        GrAccessorMethod getter1 = new GrAccessorMethodImpl(this, false, "get" + name);
        if (hasContradictingMethods(getter1, clazz)) getter1 = null;

        GrAccessorMethod getter2 = null;
        if (PsiType.BOOLEAN.equals(getDeclaredType())) {
          getter2 = new GrAccessorMethodImpl(this, false, "is" + name);
          if (hasContradictingMethods(getter2, clazz)) getter2 = null;
        }

        if (getter1 != null || getter2 != null) {
          if (getter1 != null && getter2 != null) myGetters = new GrAccessorMethod[]{getter1, getter2};
          else if (getter1 != null) myGetters = new GrAccessorMethod[]{getter1};
          else myGetters = new GrAccessorMethod[]{getter2};
        }
      }

    }

    myGettersInitialized = true;
    return myGetters;
  }

  private boolean hasContradictingMethods(GrAccessorMethod proto, PsiClass clazz) {
    PsiMethod[] methods = clazz instanceof GrTypeDefinition ?
            ((GrTypeDefinition) clazz).findCodeMethodsBySignature(proto, true) :
            clazz.findMethodsBySignature(proto, true);
    for (PsiMethod method : methods) {
      if (clazz.equals(method.getContainingClass())) return true;

      if (PsiUtil.isAccessible(clazz, method) && method.hasModifierProperty(PsiModifier.FINAL)) return true;
    }

    //final property in supers
    PsiClass aSuper = clazz.getSuperClass();
    if (aSuper != null) {
      PsiField field = aSuper.findFieldByName(getName(), true);
      if (field instanceof GrField && ((GrField) field).isProperty() && field.hasModifierProperty(PsiModifier.FINAL))
        return true;
    }

    return false;
  }

  @NotNull
  public SearchScope getUseScope() {
    if (isProperty()) {
      return getManager().getFileManager().getUseScope(this); //maximal scope
    }
    return com.intellij.psi.impl.PsiImplUtil.getMemberUseScope(this);
  }

  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass == null) return this;
    PsiClass originalClass = (PsiClass) containingClass.getOriginalElement();
    PsiField originalField = originalClass.findFieldByName(getName(), false);
    return originalField != null ? originalField : this;
  }

  public PsiElement getContext() {
    return getParent();
  }

  @Nullable
  public Icon getIcon(int flags) {
    Icon superIcon = super.getIcon(flags);
    if (!isProperty()) return superIcon;

    LayeredIcon rowIcon = new LayeredIcon(2);
    rowIcon.setIcon(superIcon, 0);
    rowIcon.setIcon(GroovyIcons.DEF, 1);
    return rowIcon;
  }
}
