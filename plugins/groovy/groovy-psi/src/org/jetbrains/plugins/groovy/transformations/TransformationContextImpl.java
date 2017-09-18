/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.transformations;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.transformations.dsl.MemberBuilder;

import java.util.*;

import static com.intellij.psi.util.MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY;
import static com.intellij.util.containers.ContainerUtil.flatten;
import static com.intellij.util.containers.ContainerUtil.map;
import static org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil.expandReflectedMethods;

public class TransformationContextImpl implements TransformationContext {

  private final @NotNull Project myProject;
  private final @NotNull PsiManager myPsiManager;
  private final @NotNull JavaPsiFacade myPsiFacade;
  private final @NotNull GrTypeDefinition myCodeClass;
  private final @NotNull PsiClassType myClassType;

  private final LinkedList<PsiMethod> myMethods = ContainerUtil.newLinkedList();
  private final Collection<GrField> myFields = ContainerUtil.newArrayList();
  private final Collection<PsiClass> myInnerClasses = ContainerUtil.newArrayList();
  private final List<PsiClassType> myImplementsTypes = ContainerUtil.newArrayList();
  private final List<PsiClassType> myExtendsTypes = ContainerUtil.newArrayList();
  private final MemberBuilder myMemberBuilder = new MemberBuilder(this);
  private final Map<String, Set<MethodSignature>> mySignaturesCache = FactoryMap.createMap(name -> {
    THashSet<MethodSignature> result = new THashSet<>(METHOD_PARAMETERS_ERASURE_EQUALITY);
    for (PsiMethod existingMethod : myMethods) {
      if (existingMethod.getName().equals(name)) {
        result.add(existingMethod.getSignature(PsiSubstitutor.EMPTY));
      }
    }
    return result;
  });

  public TransformationContextImpl(@NotNull GrTypeDefinition codeClass) {
    myProject = codeClass.getProject();
    myPsiManager = codeClass.getManager();
    myPsiFacade = JavaPsiFacade.getInstance(myProject);
    myCodeClass = codeClass;
    myClassType = getPsiFacade().getElementFactory().createType(getCodeClass());

    ContainerUtil.addAll(myFields, codeClass.getCodeFields());
    ContainerUtil.addAll(myMethods, flatten(map(codeClass.getCodeMethods(), m -> expandReflectedMethods(m))));
    ContainerUtil.addAll(myInnerClasses, codeClass.getCodeInnerClasses());
    ContainerUtil.addAll(myImplementsTypes, GrClassImplUtil.getReferenceListTypes(codeClass.getImplementsClause()));
    ContainerUtil.addAll(myExtendsTypes, GrClassImplUtil.getReferenceListTypes(codeClass.getExtendsClause()));
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public PsiManager getManager() {
    return myPsiManager;
  }

  @NotNull
  @Override
  public JavaPsiFacade getPsiFacade() {
    return myPsiFacade;
  }

  @Override
  @NotNull
  public GrTypeDefinition getCodeClass() {
    return myCodeClass;
  }

  @NotNull
  @Override
  public PsiClassType getClassType() {
    return myClassType;
  }

  @Override
  @NotNull
  public Collection<GrField> getFields() {
    return myFields;
  }

  @NotNull
  @Override
  public Collection<PsiField> getAllFields(boolean includeSynthetic) {
    if (!includeSynthetic) {
      return Arrays.asList(GrClassImplUtil.getAllFields(getCodeClass(), false));
    }

    Set<PsiField> fields = ContainerUtil.newHashSet(myFields);
    ArrayList<PsiClassType> superTypes = ContainerUtil.newArrayList(myExtendsTypes);
    superTypes.addAll(myImplementsTypes);
    for (PsiClassType type: superTypes) {
      PsiClass psiClass = type.resolve();
      if (psiClass != null) {
        ContainerUtil.addAll(fields, psiClass.getAllFields());
      }
    }
    return fields;
  }

  @NotNull
  @Override
  public Collection<PsiMethod> getMethods() {
    return myMethods;
  }

  @Override
  @NotNull
  public Collection<PsiClass> getInnerClasses() {
    return myInnerClasses;
  }

  @Override
  @NotNull
  public List<PsiClassType> getImplementsTypes() {
    return myImplementsTypes;
  }

  @Override
  @NotNull
  public List<PsiClassType> getExtendsTypes() {
    return myExtendsTypes;
  }

  @Nullable
  @Override
  public String getClassName() {
    return myCodeClass.getName();
  }

  @Override
  @Nullable
  public PsiClass getSuperClass() {
    return GrClassImplUtil.getSuperClass(getCodeClass(), getExtendsListTypesArray());
  }

  @Override
  @Nullable
  public PsiAnnotation getAnnotation(@NotNull String fqn) {
    return PsiImplUtil.getAnnotation(getCodeClass(), fqn);
  }

  @Override
  public boolean isInheritor(@NotNull PsiClass baseClass) {
    if (getManager().areElementsEquivalent(getCodeClass(), baseClass)) return false;
    if (getCodeClass().isInterface() && !baseClass.isInterface()) return false;

    for (PsiClassType superType : getSuperTypes()) {
      PsiClass superClass = superType.resolve();
      if (superClass == null) continue;
      if (getManager().areElementsEquivalent(superClass, baseClass)) return true;
      if (superClass.isInheritor(baseClass, true)) return true;
    }

    return false;
  }

  @NotNull
  @Override
  public Collection<PsiMethod> findMethodsByName(@NotNull String name, boolean checkBases) {
    List<PsiMethod> methods = ContainerUtil.filter(myMethods, m -> name.equals(m.getName()));
    if (checkBases) {
      PsiClass aClass = getSuperClass();
      if (aClass != null) ContainerUtil.addAll(ContainerUtil.newArrayList(methods), aClass.findMethodsByName(name, true));
    }
    return methods;
  }

  private void doAddMethod(@NotNull PsiMethod method, boolean prepend) {
    if (method instanceof GrLightMethodBuilder) {
      ((GrLightMethodBuilder)method).setContainingClass(myCodeClass);
    }
    else if (method instanceof LightMethodBuilder) {
      ((LightMethodBuilder)method).setContainingClass(myCodeClass);
    }
    MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
    Set<MethodSignature> signatures = mySignaturesCache.get(method.getName());
    if (signatures.add(signature)) {
      if (prepend) {
        myMethods.addFirst(method);
      }
      else {
        myMethods.addLast(method);
      }
    }
  }

  @Override
  public void addMethod(@NotNull PsiMethod method, boolean prepend) {
    for (PsiMethod expanded : expandReflectedMethods(method)) {
      doAddMethod(expanded, prepend);
    }
  }

  @Override
  public void addMethods(@NotNull PsiMethod[] methods) {
    for (PsiMethod method : methods) {
      addMethod(method);
    }
  }

  @Override
  public void addMethods(@NotNull Collection<? extends PsiMethod> methods) {
    for (PsiMethod method : methods) {
      addMethod(method);
    }
  }

  @Override
  public void removeMethod(@NotNull PsiMethod method) {
    Set<MethodSignature> signatures = mySignaturesCache.get(method.getName());
    for (PsiMethod expanded : expandReflectedMethods(method)) {
      MethodSignature signature = expanded.getSignature(PsiSubstitutor.EMPTY);
      if (signatures.remove(signature)) {
        myMethods.removeIf(m -> METHOD_PARAMETERS_ERASURE_EQUALITY.equals(signature, m.getSignature(PsiSubstitutor.EMPTY)));
      }
    }
  }

  @Override
  public void addField(@NotNull GrField field) {
    if (field instanceof GrLightField) {
      ((GrLightField)field).setContainingClass(getCodeClass());
    }
    myFields.add(field);
  }

  @Override
  public void addInnerClass(@NotNull PsiClass innerClass) {
    if (innerClass instanceof LightPsiClassBuilder) {
      ((LightPsiClassBuilder)innerClass).setContainingClass(getCodeClass());
    }
    myInnerClasses.add(innerClass);
  }

  @Override
  public void setSuperType(@NotNull String fqn) {
    setSuperType(TypesUtil.createType(fqn, getCodeClass()));
  }

  @Override
  public void setSuperType(@NotNull PsiClassType type) {
    if (!getCodeClass().isInterface()) {
      myExtendsTypes.clear();
      myExtendsTypes.add(type);
    }
  }

  @Override
  public void addInterface(@NotNull String fqn) {
    addInterface(TypesUtil.createType(fqn, getCodeClass()));
  }

  @Override
  public void addInterface(@NotNull PsiClassType type) {
    (!getCodeClass().isInterface() || getCodeClass().isTrait() ? myImplementsTypes : myExtendsTypes).add(type);
  }

  @NotNull
  @Override
  public MemberBuilder getMemberBuilder() {
    return myMemberBuilder;
  }

  @NotNull
  private GrField[] getFieldsArray() {
    return getFields().toArray(GrField.EMPTY_ARRAY);
  }

  @NotNull
  private PsiMethod[] getMethodsArray() {
    return getMethods().toArray(PsiMethod.EMPTY_ARRAY);
  }

  @NotNull
  private PsiClass[] getInnerClassesArray() {
    return getInnerClasses().toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  private PsiClassType[] getImplementsListTypesArray() {
    return getImplementsTypes().toArray(PsiClassType.EMPTY_ARRAY);
  }

  @NotNull
  private PsiClassType[] getExtendsListTypesArray() {
    if (getExtendsTypes().size() > 1 && !getCodeClass().isInterface()) {
      throw new IllegalStateException("More than one type supertype for non-interface");
    }
    return getExtendsTypes().toArray(PsiClassType.EMPTY_ARRAY);
  }

  @NotNull
  TransformationResult getTransformationResult() {
    return new TransformationResult(
      getMethodsArray(),
      getFieldsArray(),
      getInnerClassesArray(),
      getImplementsListTypesArray(),
      getExtendsListTypesArray()
    );
  }
}
