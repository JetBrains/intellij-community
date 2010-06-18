/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrVariableEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrNamedArgumentSearchVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrAccessorMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GrFieldImpl extends GrVariableBaseImpl<GrFieldStub> implements GrField {
  private GrAccessorMethod mySetter;
  private GrAccessorMethod[] myGetters;

  private boolean mySetterInitialized = false;
  private boolean myGettersInitialized = false;

  private volatile CachedValue<PsiType> myEnhancedType;

  public GrFieldImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrFieldImpl(GrFieldStub stub) {
    this(stub, GroovyElementTypes.FIELD);
  }

  public GrFieldImpl(GrFieldStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
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
    final GrFieldStub stub = getStub();
    boolean byDocTag = stub == null ? PsiImplUtil.isDeprecatedByDocTag(this) : stub.isDeprecatedByDocTag();
    if (byDocTag) {
      return true;
    }

    return PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public PsiType getTypeGroovy() {
    if (getDeclaredType() == null && getInitializer() == null) {
      final PsiType type = GrVariableEnhancer.getEnhancedType(this);
      if (type != null) {
        return type;
      }
    }
    return super.getTypeGroovy();
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent().getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass)pparent;
      }
    }

    final PsiFile file = getContainingFile();
    if (file instanceof GroovyFileBase) {
      return ((GroovyFileBase)file).getScriptClass();
    }

    return null;
  }

  public boolean isProperty() {
    final GrFieldStub stub = getStub();
    if (stub != null) {
      return stub.isProperty();
    }
    final PsiClass clazz = getContainingClass();
    if (clazz == null) return false;
    if (clazz.isInterface()) return false;
    final GrModifierList modifierList = getModifierList();
    return modifierList == null || !modifierList.hasExplicitVisibilityModifiers();
  }

  public GrAccessorMethod getSetter() {
    if (mySetterInitialized) return mySetter;

    mySetter = null;

    if (isProperty() && !hasModifierProperty(PsiModifier.FINAL)) {
      String name = GroovyPropertyUtils.getSetterName(getName());
      final GrAccessorMethod setter = new GrAccessorMethodImpl(this, true, name);
      final PsiClass clazz = getContainingClass();
      if (!hasContradictingMethods(setter, clazz)) {
        mySetter = setter;
      }

    }

    mySetterInitialized = true;
    return mySetter;
  }

  public void clearCaches() {
    mySetterInitialized = myGettersInitialized = false;
    mySetter = null;
    myGetters = GrAccessorMethod.EMPTY_ARRAY;
    myEnhancedType = null;
  }

  @NotNull
  public GrAccessorMethod[] getGetters() {
    if (myGettersInitialized) return myGetters;

    myGetters = GrAccessorMethod.EMPTY_ARRAY;

    if (isProperty()) {
      String name = getName();
      final PsiClass clazz = getContainingClass();
      GrAccessorMethod getter1 = new GrAccessorMethodImpl(this, false, GroovyPropertyUtils.getGetterNameNonBoolean(name));
      if (!hasContradictingMethods(getter1, clazz)) {
        GrAccessorMethod getter2 = null;
        if (PsiType.BOOLEAN.equals(getDeclaredType())) {
          getter2 = new GrAccessorMethodImpl(this, false, GroovyPropertyUtils.getGetterNameBoolean(name));
          if (hasContradictingMethods(getter2, clazz)) getter2 = null;
        }

        if (getter2 != null) {
          myGetters = new GrAccessorMethod[]{getter1, getter2};
        }
        else {
          myGetters = new GrAccessorMethod[]{getter1};
        }
      }
    }

    myGettersInitialized = true;
    return myGetters;
  }

  private boolean hasContradictingMethods(GrAccessorMethod proto, PsiClass clazz) {
    PsiMethod[] methods = clazz instanceof GrTypeDefinition
                          ? ((GrTypeDefinition)clazz).findCodeMethodsBySignature(proto, true)
                          : clazz.findMethodsBySignature(proto, true);
    for (PsiMethod method : methods) {
      if (clazz.equals(method.getContainingClass())) return true;

      if (PsiUtil.isAccessible(clazz, method) && method.hasModifierProperty(PsiModifier.FINAL)) return true;
    }

    //final property in supers
    PsiClass aSuper = clazz.getSuperClass();
    if (aSuper != null) {
      PsiField field = aSuper.findFieldByName(getName(), true);
      if (field instanceof GrField && ((GrField)field).isProperty() && field.hasModifierProperty(PsiModifier.FINAL)) return true;
    }

    return false;
  }

  @NotNull
  public SearchScope getUseScope() {
    if (isProperty()) {
      return getManager().getFileManager().getUseScope(this); //maximal scope
    }
    return PsiImplUtil.getMemberUseScope(this);
  }

  @NotNull
  @Override
  public String getName() {
    final GrFieldStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return getName();
      }

      @Nullable
      public String getLocationString() {
        PsiClass clazz = getContainingClass();
        if (clazz == null) return "";
        String name = clazz.getQualifiedName();
        assert name != null;
        return "(in " + name + ")";
      }

      @Nullable
      public Icon getIcon(boolean open) {
        return GrFieldImpl.this.getIcon(ICON_FLAG_VISIBILITY | ICON_FLAG_READ_STATUS);
      }

      @Nullable
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  public PsiElement getOriginalElement() {
    final PsiClass containingClass = getContainingClass();
    if (containingClass == null) return this;
    PsiClass originalClass = (PsiClass)containingClass.getOriginalElement();
    PsiField originalField = originalClass.findFieldByName(getName(), false);
    return originalField != null ? originalField : this;
  }

  @Nullable
  public Icon getIcon(int flags) {
    Icon superIcon = GroovyIcons.FIELD;
    if (!isProperty()) return superIcon;
    LayeredIcon rowIcon = new LayeredIcon(2);
    rowIcon.setIcon(superIcon, 0);
    rowIcon.setIcon(GroovyIcons.DEF, 1);
    return rowIcon;
  }

  @NotNull
  public Set<String>[] getNamedParametersArray() {
    final GrFieldStub stub = getStub();
    if (stub != null) {
      return stub.getNamedParameters();
    }
    final GrExpression initializerGroovy = getInitializerGroovy();

    if (!(initializerGroovy instanceof GrClosableBlock)) {
      return GrNamedArgumentSearchVisitor.EMPTY_SET_ARRAY;
    }

    final GrClosableBlock closure = (GrClosableBlock)initializerGroovy;
    final PsiParameter[] parameters = closure.getAllParameters();

    Set<String>[] res = new Set[parameters.length];

    Map<String, Set<String>> map = new HashMap<String, Set<String>>();

    for (int i = 0; i < parameters.length; i++) {
      final Set<String> set = new HashSet<String>();
      res[i] = set;
      map.put(parameters[i].getName(), set);
    }

    closure.accept(new GrNamedArgumentSearchVisitor(map));

    return res;
  }

  public GrDocComment getDocComment() {
    return GrDocCommentUtil.findDocComment(this);
  }
}
