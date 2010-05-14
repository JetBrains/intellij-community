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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class GroovyScriptClass extends LightElement implements GrMemberOwner, SyntheticElement {
  private final GroovyFile myFile;
  private final PsiMethod myMainMethod;
  private final PsiMethod myRunMethod;
  private static final String MAIN_METHOD_TEXT = "public static final void main(java.lang.String[] args) {}";
  private static final String RUN_METHOD_TEXT = "public java.lang.Object run() {return null;}";

  public GroovyScriptClass(GroovyFile file) {
    super(file.getManager(), file.getLanguage());
    myFile = file;
    myMainMethod = new GroovyScriptMethod(this, MAIN_METHOD_TEXT);
    myRunMethod = new GroovyScriptMethod(this, RUN_METHOD_TEXT);
  }


  public String toString() {
    return "Script Class:" + getQualifiedName();
  }

  public String getText() {
    return "class " + getName() + " {}";
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
  }

  public PsiElement copy() {
    return new GroovyScriptClass(myFile);
  }

  public PsiFile getContainingFile() {
    return myFile;
  }

  public TextRange getTextRange() {
    return myFile.getTextRange();
  }

  public boolean isValid() {
    return myFile.isValid() && myFile.getTopStatements().length > 0;
  }

  @NotNull
  public String getQualifiedName() {
    final String packName = myFile.getPackageName();
    if (packName.length() == 0) {
      return getName();
    }
    else {
      return packName + "." + getName();
    }
  }

  public boolean isInterface() {
    return false;
  }

  public boolean isWritable() {
    return true;
  }

  public boolean isAnnotationType() {
    return false;
  }

  public boolean isEnum() {
    return false;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    return myFile.add(element);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myFile.addAfter(element, anchor);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    return myFile.addBefore(element, anchor);
  }

  public PsiReferenceList getExtendsList() {
    return null;
  }


  public PsiReferenceList getImplementsList() {
    return null;
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  public PsiClass getSuperClass() {
    return JavaPsiFacade.getInstance(getProject()).findClass(GroovyFileBase.SCRIPT_BASE_CLASS_NAME, getResolveScope());
  }

  public PsiClass[] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getSupers() {
    final PsiClass superClass = getSuperClass();
    if (superClass != null) {
      return new PsiClass[]{superClass};
    }
    else {
      return PsiClass.EMPTY_ARRAY;
    }
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return new PsiClassType[]{
      JavaPsiFacade.getInstance(getProject()).getElementFactory().createTypeByFQClassName(GroovyFileBase.SCRIPT_BASE_CLASS_NAME,
                                                                                          getResolveScope())};
  }

  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptySet();
  }

  @NotNull
  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @NotNull
  public PsiMethod[] getMethods() {
    GrMethod[] methods = myFile.getTopLevelMethods();
    PsiMethod[] result = new PsiMethod[methods.length + 2];
    result[0] = myMainMethod;
    result[1] = myRunMethod;
    System.arraycopy(methods, 0, result, 2, methods.length);
    return result;
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @NotNull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    return null;
  }

  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
  }

  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return null;
  }

  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  public boolean hasTypeParameters() {
    return false;
  }

  public PsiJavaToken getLBrace() {
    return null;
  }

  public PsiJavaToken getRBrace() {
    return null;
  }

  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  // very special method!
  public PsiElement getScope() {
    return myFile;
  }

  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @NotNull
  public String getName() {
    String name = myFile.getName();
    int i = name.indexOf('.');
    return i > 0 ? name.substring(0, i) : name;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    myFile.setName(name + "." + myFile.getViewProvider().getVirtualFile().getExtension());
    return this;
  }

  public PsiModifierList getModifierList() {
    return null;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return PsiModifier.PUBLIC.equals(name);
  }

  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for (GrTopLevelDefintion defintion : myFile.getTopLevelDefinitions()) {
      if (!(defintion instanceof PsiClass)) {
        if (!ResolveUtil.processElement(processor, defintion)) return false;
      }
    }

    final PsiClass scriptClass = getSuperClass();
    //noinspection RedundantIfStatement
    if (scriptClass != null && !scriptClass.processDeclarations(new BaseScopeProcessor() {
      public boolean execute(PsiElement element, ResolveState state) {
        return !(element instanceof PsiNamedElement) || ResolveUtil.processElement(processor, (PsiNamedElement)element);
      }

      @Override
      public <T> T getHint(Key<T> hintKey) {
        return processor.getHint(hintKey);
      }
    }, state, lastParent, place)) {
      return false;
    }

    return true;
  }

  @Override
  public PsiElement getContext() {
    return myFile;
  }

  //default implementations of methods from NavigationItem
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return getName();
      }

      public String getLocationString() {
        return "(groovy script)";
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return myFile.getIcon(0);
      }
    };
  }

  public PsiElement getOriginalElement() {
    return PsiImplUtil.getOriginalElement(this, myFile);
  }

  @Nullable
  public Icon getIcon(int flags) {
    return myFile.getIcon(flags);
  }

  public void checkDelete() throws IncorrectOperationException {
  }

  public void delete() throws IncorrectOperationException {
    myFile.delete();
  }

  public <T extends GrMembersDeclaration> T addMemberDeclaration(T decl, PsiElement anchorBefore) throws IncorrectOperationException {
    return myFile.addMemberDeclaration(decl, anchorBefore);
  }
}

