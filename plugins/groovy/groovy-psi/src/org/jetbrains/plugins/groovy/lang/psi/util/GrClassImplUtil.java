/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrScriptField;
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrClassImplUtil {
  private static final Logger LOG = Logger.getInstance(GrClassImplUtil.class);

  private static final Condition<PsiClassType> IS_GROOVY_OBJECT = new Condition<PsiClassType>() {
    @Override
    public boolean value(PsiClassType psiClassType) {
      return TypesUtil.isClassType(psiClassType, GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME);
    }
  };

  private GrClassImplUtil() {
  }

  @Nullable
  public static PsiClass findInnerClassByName(GrTypeDefinition grType, String name, boolean checkBases) {
    if (!checkBases) {
      for (PsiClass inner : grType.getInnerClasses()) {
        if (name.equals(inner.getName())) return inner;
      }
      return null;
    }
    else {
      Map<String, CandidateInfo> innerClasses = CollectClassMembersUtil.getAllInnerClasses(grType, true);
      final CandidateInfo info = innerClasses.get(name);
      return info == null ? null : (PsiClass)info.getElement();
    }
  }

  @Nullable
  public static PsiClass getSuperClass(GrTypeDefinition grType) {
    final PsiClassType[] extendsList = grType.getExtendsListTypes();
    if (extendsList.length == 0) return getBaseClass(grType);
    final PsiClass superClass = extendsList[0].resolve();
    return superClass != null ? superClass : getBaseClass(grType);
  }

  @Nullable
  public static PsiClass getBaseClass(GrTypeDefinition grType) {
    if (grType.isEnum()) {
      return JavaPsiFacade.getInstance(grType.getProject()).findClass(CommonClassNames.JAVA_LANG_ENUM, grType.getResolveScope());
    }
    else {
      return JavaPsiFacade.getInstance(grType.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, grType.getResolveScope());
    }
  }

  @NotNull
  public static PsiClassType[] getExtendsListTypes(GrTypeDefinition grType) {
    final PsiClassType[] extendsTypes = getReferenceListTypes(grType.getExtendsClause());
    if (grType.isInterface()) {
      return extendsTypes;
    }

    for (PsiClassType type : extendsTypes) {
      final PsiClass superClass = type.resolve();
      if (superClass instanceof GrTypeDefinition && !superClass.isInterface() ||
          superClass != null && GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(superClass.getQualifiedName())) {
        return extendsTypes;
      }
    }

    PsiClass grObSupport = GroovyPsiManager.getInstance(grType.getProject()).findClassWithCache(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT, grType.getResolveScope());
    if (grObSupport != null) {
      final PsiClassType type = JavaPsiFacade.getInstance(grType.getProject()).getElementFactory().createType(grObSupport);
      return ArrayUtil.append(extendsTypes, type, PsiClassType.ARRAY_FACTORY);
    }
    return extendsTypes;
  }

  @NotNull
  public static PsiClassType[] getImplementsListTypes(GrTypeDefinition grType) {
    final Collection<PsiClassType> result = ContainerUtil.newLinkedHashSet();
    final PsiClassType[] implementsTypes = getReferenceListTypes(grType.getImplementsClause());
    final PsiClassType[] extendsTypes = getReferenceListTypes(grType.getExtendsClause());
    result.addAll(Arrays.asList(implementsTypes));
    result.addAll(AstTransformContributor.runContributorsForImplementsTypes(grType));
    if (!grType.isInterface() && !ContainerUtil.or(implementsTypes, IS_GROOVY_OBJECT) && !ContainerUtil.or(extendsTypes, IS_GROOVY_OBJECT)) {
      result.add(getGroovyObjectType(grType));
    }
    return result.toArray(new PsiClassType[result.size()]);
  }

  public static PsiClassType getGroovyObjectType(@NotNull PsiElement context) {
    return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME, context);
  }

  @NotNull
  public static PsiClassType[] getSuperTypes(GrTypeDefinition grType) {
    PsiClassType[] extendsList = grType.getExtendsListTypes();
    if (extendsList.length == 0) {
      extendsList = new PsiClassType[]{createBaseClassType(grType)};
    }

    return ArrayUtil.mergeArrays(extendsList, grType.getImplementsListTypes(), PsiClassType.ARRAY_FACTORY);
  }

  public static PsiClassType createBaseClassType(GrTypeDefinition grType) {
    if (grType.isEnum()) {
      return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_ENUM, grType);
    }
    return TypesUtil.getJavaLangObject(grType);
  }

  @NotNull
  public static PsiMethod[] getAllMethods(final GrTypeDefinition grType) {
    return CachedValuesManager.getCachedValue(grType, new CachedValueProvider<PsiMethod[]>() {
      @Nullable
      @Override
      public Result<PsiMethod[]> compute() {
        List<PsiMethod> list = ContainerUtil.newArrayList();
        getAllMethodsInner(grType, list, new HashSet<PsiClass>());
        return Result.create(list.toArray(new PsiMethod[list.size()]), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, grType);
      }
    });
  }

  @NotNull
  public static List<PsiMethod> getAllMethods(Collection<? extends PsiClass> classes) {
    List<PsiMethod> allMethods = new ArrayList<PsiMethod>();
    HashSet<PsiClass> visited = new HashSet<PsiClass>();

    for (PsiClass psiClass : classes) {
      getAllMethodsInner(psiClass, allMethods, visited);
    }

    return allMethods;
  }

  private static void getAllMethodsInner(PsiClass clazz, List<PsiMethod> allMethods, HashSet<PsiClass> visited) {
    if (visited.contains(clazz)) return;
    visited.add(clazz);

    ContainerUtil.addAll(allMethods, clazz.getMethods());

    final PsiClass[] supers = clazz.getSupers();
    for (PsiClass aSuper : supers) {
      getAllMethodsInner(aSuper, allMethods, visited);
    }
  }

  private static PsiClassType[] getReferenceListTypes(@Nullable GrReferenceList list) {
    if (list == null) return PsiClassType.EMPTY_ARRAY;
    return list.getReferencedTypes();
  }


  public static PsiClass[] getInterfaces(GrTypeDefinition grType) {
    final PsiClassType[] implementsListTypes = grType.getImplementsListTypes();
    List<PsiClass> result = new ArrayList<PsiClass>(implementsListTypes.length);
    for (PsiClassType type : implementsListTypes) {
      final PsiClass psiClass = type.resolve();
      if (psiClass != null) result.add(psiClass);
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  @NotNull
  public static PsiClass[] getSupers(GrTypeDefinition grType) {
    PsiClassType[] superTypes = grType.getSuperTypes();
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (PsiClassType superType : superTypes) {
      PsiClass superClass = superType.resolve();
      if (superClass != null) {
        result.add(superClass);
      }
    }

    return result.toArray(new PsiClass[result.size()]);
  }

  public static boolean processDeclarations(@NotNull GrTypeDefinition grType,
                                            @NotNull PsiScopeProcessor processor,
                                            @NotNull ResolveState state,
                                            @Nullable PsiElement lastParent,
                                            @NotNull PsiElement place) {
    if (place instanceof GrCodeReferenceElement && lastParent instanceof GrModifierList) {
      final PsiElement possibleAnnotation = PsiTreeUtil.skipParentsOfType(place, GrCodeReferenceElement.class);
      if (possibleAnnotation instanceof GrAnnotation && possibleAnnotation.getParent() == lastParent) {
        return true; //don't process class members while resolving annotation which annotates current class
      }
    }

    for (final PsiTypeParameter typeParameter : grType.getTypeParameters()) {
      if (!ResolveUtil.processElement(processor, typeParameter, state)) return false;
    }

    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(state);
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    final PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());

    boolean processInstanceMethods = (ResolveUtil.shouldProcessMethods(classHint) || ResolveUtil.shouldProcessProperties(classHint)) && shouldProcessInstanceMembers(grType, lastParent);

    LanguageLevel level = PsiUtil.getLanguageLevel(place);
    if (ResolveUtil.shouldProcessProperties(classHint)) {
      Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(grType);
      if (name != null) {
        CandidateInfo fieldInfo = fieldsMap.get(name);
        if (fieldInfo != null) {
          if (!processField(grType, processor, state, place, processInstanceMethods, substitutor, factory, level, fieldInfo)) {
            return false;
          }
        }
        else if (grType.isTrait() && lastParent != null) {
          PsiField field = findFieldByName(grType, name, false, true);
          if (field != null && field.hasModifierProperty(PsiModifier.PUBLIC)) {
            if (!processField(grType, processor, state, place, processInstanceMethods, substitutor, factory, level, new CandidateInfo(field, PsiSubstitutor.EMPTY))) {
              return false;
            }
          }
        }
      }
      else {
        for (CandidateInfo info : fieldsMap.values()) {
          if (!processField(grType, processor, state, place, processInstanceMethods, substitutor, factory, level, info)) {
            return false;
          }
        }
        if (grType.isTrait() && lastParent != null) {
          for (PsiField field : CollectClassMembersUtil.getFields(grType, true)) {
            if (field.hasModifierProperty(PsiModifier.PUBLIC)) {
              if (!processField(grType, processor, state, place, processInstanceMethods, substitutor, factory, level, new CandidateInfo(field, PsiSubstitutor.EMPTY))) {
                return false;
              }
            }
          }
        }
      }
    }

    if (ResolveUtil.shouldProcessMethods(classHint)) {
      Map<String, List<CandidateInfo>> methodsMap = CollectClassMembersUtil.getAllMethods(grType, true);
      boolean isPlaceGroovy = place.getLanguage() == GroovyLanguage.INSTANCE;
      if (name == null) {
        for (List<CandidateInfo> list : methodsMap.values()) {
          for (CandidateInfo info : list) {
            if (!processMethod(grType, processor, state, place, processInstanceMethods, substitutor, factory, level, isPlaceGroovy, info)) {
              return false;
            }
          }
        }
      }
      else {
        List<CandidateInfo> byName = methodsMap.get(name);
        if (byName != null) {
          for (CandidateInfo info : byName) {
            if (!processMethod(grType, processor, state, place, processInstanceMethods, substitutor, factory, level, isPlaceGroovy, info)) {
              return false;
            }
          }
        }
      }
    }

    final GrTypeDefinitionBody body = grType.getBody();
    if (body != null) {
      if (ResolveUtil.shouldProcessClasses(classHint)) {
        for (PsiClass innerClass : getInnerClassesForResolve(grType, lastParent, place)) {
          final String innerClassName = innerClass.getName();
          if (nameHint != null && !innerClassName.equals(nameHint.getName(state))) {
            continue;
          }

          if (!processor.execute(innerClass, state)) {
            return false;
          }
        }
      }
    }


    return true;
  }

  private static boolean processField(@NotNull GrTypeDefinition grType,
                                      @NotNull PsiScopeProcessor processor,
                                      @NotNull ResolveState state,
                                      @NotNull PsiElement place,
                                      boolean processInstanceMethods,
                                      @NotNull PsiSubstitutor substitutor,
                                      @NotNull PsiElementFactory factory,
                                      @NotNull LanguageLevel level, CandidateInfo fieldInfo) {
    final PsiField field = (PsiField)fieldInfo.getElement();
    if (!processInstanceMember(processInstanceMethods, field) || isSameDeclaration(place, field)) {
      return true;
    }
    LOG.assertTrue(field.getContainingClass() != null);
    final PsiSubstitutor finalSubstitutor = PsiClassImplUtil.obtainFinalSubstitutor(field.getContainingClass(), fieldInfo.getSubstitutor(), grType, substitutor, factory, level);

    return processor.execute(field, state.put(PsiSubstitutor.KEY, finalSubstitutor));
  }

  private static boolean processMethod(@NotNull GrTypeDefinition grType,
                                       @NotNull PsiScopeProcessor processor,
                                       @NotNull ResolveState state,
                                       @NotNull PsiElement place,
                                       boolean processInstanceMethods,
                                       @NotNull PsiSubstitutor substitutor,
                                       @NotNull PsiElementFactory factory,
                                       @NotNull LanguageLevel level,
                                       boolean placeGroovy,
                                       @NotNull CandidateInfo info) {
    PsiMethod method = (PsiMethod)info.getElement();
    if (!processInstanceMember(processInstanceMethods, method) || isSameDeclaration(place, method) || !isMethodVisible(placeGroovy, method)) {
      return true;
    }
    LOG.assertTrue(method.getContainingClass() != null);
    final PsiSubstitutor finalSubstitutor = PsiClassImplUtil.obtainFinalSubstitutor(method.getContainingClass(), info.getSubstitutor(), grType, substitutor, factory, level);

    return processor.execute(method, state.put(PsiSubstitutor.KEY, finalSubstitutor));
  }

  private static boolean shouldProcessInstanceMembers(@NotNull GrTypeDefinition grType, @Nullable PsiElement lastParent) {
    if (lastParent != null) {
      final GrModifierList modifierList = grType.getModifierList();
      if (modifierList != null && modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_CATEGORY) != null) {
        return false;
      }
    }
    return true;
  }

  private static boolean processInstanceMember(boolean shouldProcessInstance, @NotNull PsiMember member) {
    if (shouldProcessInstance) return true;

    if (member instanceof GrReflectedMethod) {
      return ((GrReflectedMethod)member).getBaseMethod().hasModifierProperty(PsiModifier.STATIC);
    }
    else {
      return member.hasModifierProperty(PsiModifier.STATIC);
    }
  }

  @NotNull
  private static List<PsiClass> getInnerClassesForResolve(@NotNull final GrTypeDefinition grType,
                                                          @Nullable final PsiElement lastParent,
                                                          @NotNull final PsiElement place) {
    if (lastParent instanceof GrReferenceList || PsiTreeUtil.getParentOfType(place, GrReferenceList.class) != null) {
      return Arrays.asList(grType.getInnerClasses());
    }

    List<PsiClass> classes = RecursionManager.doPreventingRecursion(grType, true, new Computable<List<PsiClass>>() {
      @Override
      public List<PsiClass> compute() {
        List<PsiClass> result = new ArrayList<PsiClass>();
        for (CandidateInfo info : CollectClassMembersUtil.getAllInnerClasses(grType, false).values()) {
          final PsiClass inner = (PsiClass)info.getElement();
          final PsiClass containingClass = inner.getContainingClass();
          assert containingClass != null;

          if (lastParent == null || !containingClass.isInterface() || PsiTreeUtil.isAncestor(containingClass, place, false)) {
            ContainerUtil.addIfNotNull(result, inner);
          }
        }
        return result;
      }
    });

    if (classes == null) {
      return Arrays.asList(grType.getInnerClasses());
    }

    return classes;
  }

  public static boolean isSameDeclaration(PsiElement place, PsiElement element) {
    if (element instanceof GrAccessorMethod) element = ((GrAccessorMethod)element).getProperty();

    if (!(element instanceof GrField)) return false;
    if (element instanceof GrScriptField) element = ((GrScriptField)element).getOriginalVariable();

    while (place != null) {
      if (place == element) return true;
      place = place.getParent();
      if (place instanceof GrClosableBlock) return false;
      if (place instanceof GrEnumConstantInitializer) return false;
    }
    return false;
  }

  private static boolean isMethodVisible(boolean isPlaceGroovy, PsiMethod method) {
    return isPlaceGroovy || !(method instanceof GrGdkMethod);
  }

  @Nullable
  public static PsiMethod findMethodBySignature(GrTypeDefinition grType, PsiMethod patternMethod, boolean checkBases) {
    final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (PsiMethod method : findMethodsByName(grType, patternMethod.getName(), checkBases, false)) {
      MethodSignature signature = getSignatureForInheritor(method, grType);
      if (patternSignature.equals(signature)) return method;
    }

    return null;
  }

  private static PsiMethod[] findMethodsByName(GrTypeDefinition grType,
                                               String name,
                                               boolean checkBases,
                                               boolean includeSyntheticAccessors) {
    if (!checkBases) {
      List<PsiMethod> result = new ArrayList<PsiMethod>();
      for (PsiMethod method : CollectClassMembersUtil.getMethods(grType, includeSyntheticAccessors)) {
        if (name.equals(method.getName())) result.add(method);
      }

      return result.toArray(new PsiMethod[result.size()]);
    }

    Map<String, List<CandidateInfo>> methodsMap = CollectClassMembersUtil.getAllMethods(grType, includeSyntheticAccessors);
    return PsiImplUtil.mapToMethods(methodsMap.get(name));
  }

  @NotNull
  public static PsiMethod[] findMethodsBySignature(GrTypeDefinition grType, PsiMethod patternMethod, boolean checkBases) {
    return findMethodsBySignature(grType, patternMethod, checkBases, true);
  }

  @NotNull
  public static PsiMethod[] findCodeMethodsBySignature(GrTypeDefinition grType, PsiMethod patternMethod, boolean checkBases) {
    return findMethodsBySignature(grType, patternMethod, checkBases, false);
  }

  @NotNull
  public static PsiMethod[] findMethodsByName(GrTypeDefinition grType, @NonNls String name, boolean checkBases) {
    return findMethodsByName(grType, name, checkBases, true);
  }

  private static PsiMethod[] findMethodsBySignature(GrTypeDefinition grType,
                                                    PsiMethod patternMethod,
                                                    boolean checkBases,
                                                    boolean includeSynthetic) {
    ArrayList<PsiMethod> result = new ArrayList<PsiMethod>();
    final MethodSignature patternSignature = patternMethod.getSignature(PsiSubstitutor.EMPTY);
    for (PsiMethod method : findMethodsByName(grType, patternMethod.getName(), checkBases, includeSynthetic)) {
      MethodSignature signature = getSignatureForInheritor(method, grType);
      if (patternSignature.equals(signature)) {
        result.add(method);
      }
    }
    return result.toArray(new PsiMethod[result.size()]);
  }

  @Nullable
  private static MethodSignature getSignatureForInheritor(@NotNull PsiMethod methodFromSuperClass, @NotNull GrTypeDefinition inheritor) {
    final PsiClass clazz = methodFromSuperClass.getContainingClass();
    if (clazz == null) return null;
    PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(clazz, inheritor, PsiSubstitutor.EMPTY);
    if (superSubstitutor == null) return null;

    return methodFromSuperClass.getSignature(superSubstitutor);
  }


  @NotNull
  public static PsiMethod[] findCodeMethodsByName(GrTypeDefinition grType, @NonNls String name, boolean checkBases) {
    return findMethodsByName(grType, name, checkBases, false);
  }

  @NotNull
  public static List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(GrTypeDefinition grType,
                                                                                            String name,
                                                                                            boolean checkBases) {
    final ArrayList<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();

    if (!checkBases) {
      final PsiMethod[] methods = grType.findMethodsByName( name, false);
      for (PsiMethod method : methods) {
        result.add(Pair.create(method, PsiSubstitutor.EMPTY));
      }
    }
    else {
      final Map<String, List<CandidateInfo>> map = CollectClassMembersUtil.getAllMethods(grType, true);
      final List<CandidateInfo> candidateInfos = map.get(name);
      if (candidateInfos != null) {
        for (CandidateInfo info : candidateInfos) {
          final PsiElement element = info.getElement();
          result.add(Pair.create((PsiMethod)element, info.getSubstitutor()));
        }
      }
    }

    return result;
  }

  @NotNull
  public static List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors(GrTypeDefinition grType) {
    final Map<String, List<CandidateInfo>> allMethodsMap = CollectClassMembersUtil.getAllMethods(grType, true);
    List<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<Pair<PsiMethod, PsiSubstitutor>>();
    for (List<CandidateInfo> infos : allMethodsMap.values()) {
      for (CandidateInfo info : infos) {
        result.add(Pair.create((PsiMethod)info.getElement(), info.getSubstitutor()));
      }
    }

    return result;
  }

  @Nullable
  public static PsiField findFieldByName(GrTypeDefinition grType, String name, boolean checkBases, boolean includeSynthetic) {
    if (!checkBases) {
      for (PsiField field : CollectClassMembersUtil.getFields(grType, includeSynthetic)) {
        if (name.equals(field.getName())) return field;
      }

      return null;
    }

    Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(grType, includeSynthetic);
    final CandidateInfo info = fieldsMap.get(name);
    return info == null ? null : (PsiField)info.getElement();
  }

  public static PsiField[] getAllFields(GrTypeDefinition grType) {
    Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(grType);
    return ContainerUtil.map2Array(fieldsMap.values(), PsiField.class, new Function<CandidateInfo, PsiField>() {
      @Override
      public PsiField fun(CandidateInfo entry) {
        return (PsiField)entry.getElement();
      }
    });
  }

  public static boolean isClassEquivalentTo(GrTypeDefinitionImpl definition, PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(definition, another);
  }

  public static void addExpandingReflectedMethods(Collection<PsiMethod> result, PsiMethod method) {
    if (method instanceof GrMethod) {
      final GrReflectedMethod[] reflectedMethods = ((GrMethod)method).getReflectedMethods();
      if (reflectedMethods.length > 0) {
        result.addAll(Arrays.asList(reflectedMethods));
        return;
      }
    }
    result.add(method);
  }

  public static void collectMethodsFromBody(@NotNull GrTypeDefinition definition, Collection<PsiMethod> result) {
    for (GrMethod method : definition.getCodeMethods()) {
      addExpandingReflectedMethods(result, method);
    }

    for (GrField field : definition.getFields()) {
      if (!field.isProperty()) continue;
      ContainerUtil.addAll(result, field.getGetters());
      ContainerUtil.addIfNotNull(result, field.getSetter());
    }
  }

  public static Collection<PsiMethod> filterOutAccessors(Collection<PsiMethod> result) {
    final TObjectIntHashMap<String> map = new TObjectIntHashMap<String>();
    for (PsiMethod method : result) {
      if (method instanceof GrAccessorMethod || GroovyPropertyUtils.isSimplePropertyAccessor(method)) {
        final String methodName = method.getName();
        if (map.containsKey(methodName)) {
          map.adjustValue(methodName, 1);
        }
        else {
          map.put(methodName, 1);
        }
      }
    }
    return ContainerUtil.filter(result, new Condition<PsiMethod>() {
      @Override
      public boolean value(PsiMethod method) {
        return !(method instanceof GrAccessorMethod) || map.get(method.getName()) <= 1;
      }
    });
  }
}
