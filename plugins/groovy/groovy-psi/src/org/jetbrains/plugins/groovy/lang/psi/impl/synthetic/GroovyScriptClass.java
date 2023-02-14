// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.JavaPsiImplementationHelper;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import icons.JetgroovyIcons;
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

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessMethods;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessProperties;

public final class GroovyScriptClass extends GrLightTypeDefinitionBase implements SyntheticElement {
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
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement copy() {
    return new GroovyScriptClass(myFile);
  }

  @NotNull
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

  @NotNull
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

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes(boolean includeSynthetic) {
    return myCache.getExtendsListTypes(includeSynthetic);
  }

  @Override
  public PsiClassType @NotNull [] getImplementsListTypes(boolean includeSynthetic) {
    return myCache.getImplementsListTypes(includeSynthetic);
  }

  @Override
  public GrField @NotNull [] getFields() {
    return myCache.getFields();
  }

  @Override
  public PsiMethod @NotNull [] getMethods() {
    return myCache.getMethods();
  }

  @Override
  public PsiMethod @NotNull [] getConstructors() {
    return myCache.getConstructors();
  }

  @Override
  public PsiClass @NotNull [] getInnerClasses() {
    return myCache.getInnerClasses();
  }

  @Override
  public GrField @NotNull [] getCodeFields() {
    return GrField.EMPTY_ARRAY;
  }

  @Override
  public GrMethod @NotNull [] getCodeConstructors() {
    return GrMethod.EMPTY_ARRAY;
  }

  @Override
  public GrMethod @NotNull [] getCodeMethods() {
    return myCache.getCodeMethods();
  }

  // very special method!
  @Override
  public PsiElement getScope() {
    return myFile;
  }

  @Override
  @NotNull
  @NlsSafe
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
    if (shouldProcessMethods(processor) || shouldProcessProperties(processor)) {
      return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
    }
    else {
      return true;
    }
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
  public PsiElement getOriginalElement() {
    return JavaPsiImplementationHelper.getInstance(getProject()).getOriginalClass(this);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Override
  public Icon getIcon(int flags) {
    RowIcon baseIcon = IconManager.getInstance().createLayeredIcon(this, JetgroovyIcons.Groovy.Class, 0);
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

