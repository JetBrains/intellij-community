/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * @author ven
 */
public class GroovyScriptClass extends LightElement implements PsiClass, SyntheticElement {
  private final GroovyFile myFile;
  private volatile PsiMethod myMainMethod = null;
  private volatile PsiMethod myRunMethod = null;

  private volatile boolean myInitialized = false;

  private final LightModifierList myModifierList;

  public GroovyScriptClass(GroovyFile file) {
    super(file.getManager(), file.getLanguage());
    myFile = file;

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

  @Nullable
  public String getQualifiedName() {
    final String name = getName();
    if (name == null) return null;

    final String packName = myFile.getPackageName();
    if (packName.length() == 0) {
      return name;
    }
    else {
      return packName + "." + name;
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
    PsiClassType type = getSuperClassTypeFromBaseScriptAnnotatedVariable();
    if (type != null) {
      return new PsiClassType[]{type};
    }

    final PsiClassType superClassFromDSL = GroovyDslFileIndex.pocessScriptSuperClasses(myFile);
    if (superClassFromDSL != null) {
      return new PsiClassType[]{superClassFromDSL};
    }

    PsiClassType superClass = TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_SCRIPT, this);
    return new PsiClassType[]{superClass};
  }

  @Nullable
  private PsiClassType getSuperClassTypeFromBaseScriptAnnotatedVariable() {
    return RecursionManager.doPreventingRecursion(this, false, new Computable<PsiClassType>() {
      @Override
      public PsiClassType compute() {
        return CachedValuesManager.getCachedValue(myFile, new CachedValueProvider<PsiClassType>() {
          @Nullable
          @Override
          public Result<PsiClassType> compute() {
            GrVariableDeclaration declaration = findDeclaration();
            if (declaration != null) {
              GrModifierList modifierList = declaration.getModifierList();
              if (modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_BASE_SCRIPT) != null) {
                GrTypeElement typeElement = declaration.getTypeElementGroovy();
                if (typeElement != null) {
                  PsiType type = typeElement.getType();
                  if (type instanceof PsiClassType) {
                    return Result.create(((PsiClassType)type), myFile);
                  }
                }
              }
            }

            return Result.create(null, myFile);
          }
        });
      }
    });
  }

  @Nullable
  private GrVariableDeclaration findDeclaration() {
    final Ref<GrVariableDeclaration> ref = Ref.create();
    myFile.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
        super.visitVariableDeclaration(variableDeclaration);
        if (variableDeclaration.getModifierList().findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_BASE_SCRIPT) != null) {
          ref.set(variableDeclaration);
        }
      }

      @Override
      public void visitElement(GroovyPsiElement element) {
        if (ref.isNull()) {
          super.visitElement(element);
        }
      }
    });

    return ref.get();
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
    return GrScriptField.getScriptFields(this);
  }

  @NotNull
  public PsiMethod[] getMethods() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiMethod[]>() {
      @Nullable
      @Override
      public Result<PsiMethod[]> compute() {
        if (!myInitialized) {
          initMethods();
        }

        PsiMethod[] methods = myFile.getMethods();

        int addMain = hasMain(methods) ? 0 : 1;
        int addRun = hasRun(methods) ? 0 : 1;

        PsiMethod[] result = initMethods(methods, addMain, addRun);
        return Result.create(result, myFile);
      }
    });
  }

  private PsiMethod[] initMethods(PsiMethod[] methods, int addMain, int addRun) {
    if (addMain + addRun == 0) {
      return methods;
    }

    PsiMethod[] result = new PsiMethod[methods.length + addMain + addRun];
    if (addMain == 1) result[0] = myMainMethod;
    if (addRun == 1) result[addMain] = myRunMethod;
    System.arraycopy(methods, 0, result, addMain + addRun, methods.length);
    return result;
  }

  private boolean hasMain(@NotNull PsiMethod[] methods) {
    assert myMainMethod != null;
    return ContainerUtil.find(methods, new Condition<PsiMethod>() {
      @Override
      public boolean value(PsiMethod method) {
        return method.isEquivalentTo(myMainMethod);
      }
    }) != null;
  }

  private boolean hasRun(@NotNull PsiMethod[] methods) {
    assert myRunMethod != null;
    return ContainerUtil.find(methods, new Condition<PsiMethod>() {
      @Override
      public boolean value(PsiMethod method) {
        return method.isEquivalentTo(myRunMethod);
      }
    }) != null;
  }

  private synchronized void initMethods() {
    if (myInitialized) return;
    myMainMethod = new LightMethodBuilder(getManager(), GroovyFileType.GROOVY_LANGUAGE, "main").
      setContainingClass(this).
      setMethodReturnType(PsiType.VOID).
      addParameter("args", new PsiArrayType(PsiType.getJavaLangString(getManager(), getResolveScope()))).
      addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
    myRunMethod = new LightMethodBuilder(getManager(), GroovyFileType.GROOVY_LANGUAGE, "run").
      setContainingClass(this).
      setMethodReturnType(PsiType.getJavaLangObject(getManager(), getResolveScope())).
      addModifier(PsiModifier.PUBLIC);

    myInitialized = true;
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
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
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
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
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

  @Nullable
  public String getName() {
    String fileName = myFile.getName();
    final String name = FileUtilRt.getNameWithoutExtension(fileName);
    if (StringUtil.isJavaIdentifier(name)) {
      return name;
    }
    else {
      return null;
    }
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

  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, ContainerUtil.<PsiClass>newHashSet(), lastParent, place,
                                                       PsiUtil.getLanguageLevel(place), false);
  }

  @Override
  public PsiElement getContext() {
    return myFile;
  }

  //default implementations of methods from NavigationItem
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final String name = getName();
        return name != null ? name : "<unnamed>";
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

