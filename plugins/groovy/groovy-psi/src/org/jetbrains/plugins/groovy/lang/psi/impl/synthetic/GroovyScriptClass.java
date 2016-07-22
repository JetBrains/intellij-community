/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.ui.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrTypeDefinitionMembersCache;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.code.FileCodeMembersProvider;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;

import javax.swing.*;

public class GroovyScriptClass extends GrLightTypeDefinitionBase implements SyntheticElement {

  private final GroovyFile myFile;
  private final GrTypeDefinitionMembersCache<GroovyScriptClass> myCache;

  public GroovyScriptClass(@NotNull GroovyFile file) {
    super(file);
    myFile = file;
    myCache = new GrTypeDefinitionMembersCache<>(this, FileCodeMembersProvider.INSTANCE);
    getModifierList().addModifier(GrModifierFlags.PUBLIC_MASK);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
  }

  @Override
  public PsiElement copy() {
    return new GroovyScriptClass(myFile);
  }

  @Override
  public GroovyFile getContainingFile() {
    return myFile;
  }

  @Override
  public TextRange getTextRange() {
    return myFile.getTextRange();
  }

  @Override
  public int getTextOffset() {
    return 0;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid() && myFile.isScript();
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    return StringUtil.getQualifiedName(myFile.getPackageName(), getName());
  }

  @Override
  public boolean isWritable() {
    return myFile.isWritable();
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return myFile.add(element);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return myFile.addAfter(element, anchor);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, @Nullable PsiElement anchor) throws IncorrectOperationException {
    return myFile.addBefore(element, anchor);
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes(boolean includeSynthetic) {
    return myCache.getExtendsListTypes(includeSynthetic);
  }

  @NotNull
  @Override
  public PsiClassType[] getImplementsListTypes(boolean includeSynthetic) {
    return myCache.getImplementsListTypes(includeSynthetic);
  }

  @NotNull
  @Override
  public GrField[] getFields() {
    return myCache.getFields();
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myCache.getMethods();
  }

  @NotNull
  @Override
  public PsiMethod[] getConstructors() {
    return myCache.getConstructors();
  }

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    return myCache.getInnerClasses();
  }

  @NotNull
  @Override
  public GrField[] getCodeFields() {
    return GrField.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public GrMethod[] getCodeConstructors() {
    return GrMethod.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public GrMethod[] getCodeMethods() {
    return myCache.getCodeMethods();
  }

  // very special method!
  @Override
  public PsiElement getScope() {
    return myFile;
  }

  @Override
  @NotNull
  public String getName() {
    return FileUtilRt.getNameWithoutExtension(myFile.getName());
  }

  @Override
  public PsiElement setName(@NotNull @NonNls String name) throws IncorrectOperationException {
    myFile.setName(PathUtil.makeFileName(name, myFile.getViewProvider().getVirtualFile().getExtension()));
    return this;
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
  }

  @Override
  public PsiElement getContext() {
    return myFile;
  }

  //default implementations of methods from NavigationItem
  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Override
      public String getPresentableText() {
        return getName();
      }

      @Override
      public String getLocationString() {
        final String packageName = myFile.getPackageName();
        return "(groovy script" + (packageName.isEmpty() ? "" : ", " + packageName) + ")";
      }

      @Override
      public Icon getIcon(boolean open) {
        return GroovyScriptClass.this.getIcon(ICON_FLAG_VISIBILITY | ICON_FLAG_READ_STATUS);
      }
    };
  }

  @Override
  @Nullable
  public PsiElement getOriginalElement() {
    return JavaPsiImplementationHelper.getInstance(getProject()).getOriginalClass(this);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  @Nullable
  public Icon getIcon(int flags) {
    final Icon icon = myFile.getIcon(flags);
    RowIcon baseIcon = ElementBase.createLayeredIcon(this, icon, 0);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
  }

  @Override
  public void delete() throws IncorrectOperationException {
    myFile.delete();
  }
}

