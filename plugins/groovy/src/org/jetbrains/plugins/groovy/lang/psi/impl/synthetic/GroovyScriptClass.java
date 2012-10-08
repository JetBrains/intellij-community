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
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.RowIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class GroovyScriptClass extends LightElement implements PsiClass, SyntheticElement {
  private final GroovyFile myFile;
  private final PsiMethod myMainMethod;
  private final PsiMethod myRunMethod;

  private final LightModifierList myModifierList;

  public GroovyScriptClass(GroovyFile file) {
    super(file.getManager(), file.getLanguage());
    myFile = file;
    myMainMethod = new LightMethodBuilder(getManager(), GroovyFileType.GROOVY_LANGUAGE, "main").
      setContainingClass(this).
      setMethodReturnType(PsiType.VOID).
      addParameter("args", new PsiArrayType(PsiType.getJavaLangString(getManager(), getResolveScope()))).
      addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
    myRunMethod = new LightMethodBuilder(getManager(), GroovyFileType.GROOVY_LANGUAGE, "run").
      setContainingClass(this).
      setMethodReturnType(PsiType.getJavaLangObject(getManager(), getResolveScope())).
      addModifier(PsiModifier.PUBLIC);

    myModifierList = new LightModifierList(myManager, GroovyFileType.GROOVY_LANGUAGE, PsiModifier.PUBLIC);
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

  public GroovyFile getContainingFile() {
    return myFile;
  }

  public TextRange getTextRange() {
    return myFile.getTextRange();
  }

  @Override
  public int getTextOffset() {
    return 0;
  }

  public boolean isValid() {
    return myFile.isValid() && myFile.isScript();
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
    return myFile.isWritable();
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
    return new PsiClassType[]{ TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_SCRIPT, this)};
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @NotNull
  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @NotNull
  public PsiField[] getFields() {
    return getScriptFields();
  }

  @NotNull
  public PsiMethod[] getMethods() {
    GrMethod[] methods = myFile.getMethods();
    byte hasMain = 1;
    byte hasRun = 1;
    for (GrMethod method : methods) {
      if (method.isEquivalentTo(myMainMethod)) hasMain = 0;
      else if (method.isEquivalentTo(myRunMethod)) hasRun = 0;
    }
    if (hasMain + hasRun == 0) return methods;

    PsiMethod[] result = new PsiMethod[methods.length + hasMain + hasRun];
    if (hasMain == 1) result[0] = myMainMethod;
    if (hasRun == 1) result[hasMain] = myRunMethod;
    System.arraycopy(methods, 0, result, hasMain + hasRun, methods.length);
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
    return myModifierList;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  public PsiDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return false;
  }

  private GrField[] getScriptFields() {
    return CachedValuesManager.getManager(getProject()).getCachedValue(this, new CachedValueProvider<GrField[]>() {
      @Override
      public Result<GrField[]> compute() {
        List<GrField> result = RecursionManager.doPreventingRecursion(GroovyScriptClass.this, true, new Computable<List<GrField>>() {
          @Override
          public List<GrField> compute() {
            final List<GrField> result = new ArrayList<GrField>();
            myFile.accept(new GroovyRecursiveElementVisitor() {
              @Override
              public void visitVariableDeclaration(GrVariableDeclaration element) {
                if (element.getModifierList().findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD) != null) {
                  for (GrVariable variable : element.getVariables()) {
                    result.add(GrScriptField.createScriptFieldFrom(variable));
                  }
                }
                super.visitVariableDeclaration(element);
              }
            });
            return result;
          }
        });

        if (result == null) {
          result = Collections.emptyList();
        }
        return Result.create(result.toArray(new GrField[result.size()]), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, myFile);
      }
    });
  }

  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    for (GrTopLevelDefinition definition : myFile.getTopLevelDefinitions()) {
      if (!(definition instanceof PsiClass)) {
        if (!ResolveUtil.processElement(processor, definition, state)) return false;
      }
    }

    for (GrVariable variable : getScriptFields()) {
      if (!GrClassImplUtil.isSameDeclaration(place, variable)) {
        if (!ResolveUtil.processElement(processor, variable, state)) {
          return false;
        }
      }
    }

    if (!ResolveUtil.processElement(processor, myMainMethod, state) || !ResolveUtil.processElement(processor, myRunMethod, state)) {
      return false;
    }

    final PsiClass scriptClass = getSuperClass();
    //noinspection RedundantIfStatement
    if (scriptClass != null && !scriptClass.processDeclarations(new BaseScopeProcessor() {
      public boolean execute(@NotNull PsiElement element, ResolveState state) {
        return !(element instanceof PsiNamedElement) || ResolveUtil.processElement(processor, (PsiNamedElement)element, state);
      }

      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
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
        final String packageName = myFile.getPackageName();
        return "(groovy script" + (packageName.isEmpty() ? "" : ", " + packageName) + ")";
      }

      public Icon getIcon(boolean open) {
        return GroovyScriptClass.this.getIcon(ICON_FLAG_VISIBILITY | ICON_FLAG_READ_STATUS);
      }
    };
  }

  @Nullable
  public PsiElement getOriginalElement() {
    return PsiImplUtil.getOriginalElement(this, myFile);
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  @Nullable
  public Icon getIcon(int flags) {
    final Icon icon = myFile.getIcon(flags);
    RowIcon baseIcon = ElementBase.createLayeredIcon(this, icon, 0);
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  public void checkDelete() throws IncorrectOperationException {
  }

  public void delete() throws IncorrectOperationException {
    myFile.delete();
  }

}

