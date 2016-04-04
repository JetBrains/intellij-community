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
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
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

    myModifierList = new LightModifierList(myManager, GroovyLanguage.INSTANCE, PsiModifier.PUBLIC);
  }


  public String toString() {
    return "Script Class:" + getQualifiedName();
  }

  @Override
  public String getText() {
    return "class " + getName() + " {}";
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

  @Override
  @Nullable
  public String getQualifiedName() {
    final String name = getName();
    final String packName = myFile.getPackageName();
    if (packName.isEmpty()) {
      return name;
    }
    else {
      return packName + "." + name;
    }
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isWritable() {
    return myFile.isWritable();
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
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

  @Override
  public PsiReferenceList getExtendsList() {
    return null;
  }


  @Override
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    PsiClassType type = getSuperClassTypeFromBaseScriptAnnotatedVariable();
    if (type != null) {
      return new PsiClassType[]{type};
    }

    final PsiClassType superClassFromDSL = GroovyDslFileIndex.processScriptSuperClasses(myFile);
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
        return CachedValuesManager.getCachedValue(GroovyScriptClass.this, new CachedValueProvider<PsiClassType>() {
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

  @Override
  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @Override
  @NotNull
  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  @NotNull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  @NotNull
  public PsiField[] getFields() {
    return GrScriptField.getScriptFields(this);
  }

  @Override
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
    myMainMethod = new LightMethodBuilder(getManager(), GroovyLanguage.INSTANCE, "main").
      setContainingClass(this).
      setMethodReturnType(PsiType.VOID).
      addParameter("args", new PsiArrayType(PsiType.getJavaLangString(getManager(), getResolveScope()))).
      addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);
    myRunMethod = new LightMethodBuilder(getManager(), GroovyLanguage.INSTANCE, "run").
      setContainingClass(this).
      setMethodReturnType(TypesUtil.getJavaLangObject(this)).
      addModifier(PsiModifier.PUBLIC);

    myInitialized = true;
  }

  @Override
  @NotNull
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Override
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Override
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Override
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  // very special method!
  @Override
  public PsiElement getScope() {
    return myFile;
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  @NotNull
  public String getName() {
    return FileUtilRt.getNameWithoutExtension(myFile.getName());
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    myFile.setName(PathUtil.makeFileName(name, myFile.getViewProvider().getVirtualFile().getExtension()));
    return this;
  }

  @Override
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return myModifierList.hasModifierProperty(name);
  }

  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
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

