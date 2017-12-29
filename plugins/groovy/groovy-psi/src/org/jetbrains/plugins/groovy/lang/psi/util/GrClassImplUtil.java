// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrScriptField;
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.isAnnotationResolve;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.shouldProcessTypeParameters;

/**
 * @author Maxim.Medvedev
 */
public class GrClassImplUtil {
  private static final Logger LOG = Logger.getInstance(GrClassImplUtil.class);

  private GrClassImplUtil() {
  }

  private static final Condition<PsiMethod> CONSTRUCTOR_CONDITION = PsiMethod::isConstructor;

  @NotNull
  public static GrMethod[] getCodeConstructors(@NotNull GrTypeDefinition definition) {
    GrMethod[] methods = definition.getCodeMethods();
    List<GrMethod> result = ContainerUtil.filter(methods, CONSTRUCTOR_CONDITION);
    return result.toArray(GrMethod.EMPTY_ARRAY);
  }

  @NotNull
  public static PsiMethod[] getConstructors(@NotNull GrTypeDefinition definition) {
    PsiMethod[] methods = definition.getMethods();
    List<PsiMethod> result = ContainerUtil.filter(methods, CONSTRUCTOR_CONDITION);
    return result.toArray(PsiMethod.EMPTY_ARRAY);
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
  public static PsiClass getSuperClass(@NotNull GrTypeDefinition grType) {
    return getSuperClass(grType, grType.getExtendsListTypes());
  }

  @Nullable
  public static PsiClass getSuperClass(@NotNull GrTypeDefinition grType, @NotNull PsiClassType[] extendsListTypes) {
    if (extendsListTypes.length == 0) return getBaseClass(grType);
    final PsiClass superClass = extendsListTypes[0].resolve();
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
  public static PsiClassType[] getSuperTypes(GrTypeDefinition grType, boolean includeSynthetic) {
    PsiClassType[] extendsList = grType.getExtendsListTypes(includeSynthetic);
    if (extendsList.length == 0) {
      extendsList = new PsiClassType[]{createBaseClassType(grType)};
    }

    return ArrayUtil.mergeArrays(extendsList, grType.getImplementsListTypes(includeSynthetic), PsiClassType.ARRAY_FACTORY);
  }

  public static PsiClassType createBaseClassType(GrTypeDefinition grType) {
    if (grType.isEnum()) {
      return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_ENUM, grType);
    }
    return TypesUtil.getJavaLangObject(grType);
  }

  @NotNull
  public static PsiMethod[] getAllMethods(final GrTypeDefinition grType) {
    return CachedValuesManager.getCachedValue(grType, () -> {
      List<PsiMethod> list = ContainerUtil.newArrayList();
      getAllMethodsInner(grType, list, new HashSet<>());
      return CachedValueProvider.Result
        .create(list.toArray(new PsiMethod[list.size()]), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, grType);
    });
  }

  @NotNull
  public static List<PsiMethod> getAllMethods(Collection<? extends PsiClass> classes) {
    List<PsiMethod> allMethods = new ArrayList<>();
    HashSet<PsiClass> visited = new HashSet<>();

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

  public static PsiClassType[] getReferenceListTypes(@Nullable GrReferenceList list) {
    if (list == null) return PsiClassType.EMPTY_ARRAY;
    return list.getReferencedTypes();
  }

  @NotNull
  public static PsiClass[] getInterfaces(GrTypeDefinition grType) {
    return getInterfaces(grType, true);
  }

  @NotNull
  public static PsiClass[] getInterfaces(GrTypeDefinition grType, boolean includeSynthetic) {
    final PsiClassType[] implementsListTypes = grType.getImplementsListTypes(includeSynthetic);
    List<PsiClass> result = new ArrayList<>(implementsListTypes.length);
    for (PsiClassType type : implementsListTypes) {
      final PsiClass psiClass = type.resolve();
      if (psiClass != null) result.add(psiClass);
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  @NotNull
  public static PsiClass[] getSupers(GrTypeDefinition grType, boolean includeSynthetic) {
    PsiClassType[] superTypes = grType.getSuperTypes(includeSynthetic);
    List<PsiClass> result = new ArrayList<>();
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
    if (isAnnotationResolve(processor)) return true; //don't process class members while resolving annotation

    if (shouldProcessTypeParameters(processor)) {
      for (final PsiTypeParameter typeParameter : grType.getTypeParameters()) {
        if (!ResolveUtil.processElement(processor, typeParameter, state)) return false;
      }
    }

    NameHint nameHint = processor.getHint(NameHint.KEY);
    String name = nameHint == null ? null : nameHint.getName(state);
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    final PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());

    boolean processInstanceMethods = (ResolveUtil.shouldProcessMethods(classHint) || ResolveUtil.shouldProcessProperties(classHint)) &&
                                     shouldProcessInstanceMembers(grType, lastParent);

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
            if (!processField(grType, processor, state, place, processInstanceMethods, substitutor, factory, level,
                              new CandidateInfo(field, PsiSubstitutor.EMPTY))) {
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
              if (!processField(grType, processor, state, place, processInstanceMethods, substitutor, factory, level,
                                new CandidateInfo(field, PsiSubstitutor.EMPTY))) {
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

    if (ResolveUtil.shouldProcessClasses(classHint)) {
      Map<String, CandidateInfo> classes = CollectClassMembersUtil.getAllInnerClasses(grType, true);
      if (name == null) {
        for (CandidateInfo info : classes.values()) {
          if (!processor.execute(info.getElement(), state)) return false;
        }
      }
      else {
        CandidateInfo info = classes.get(name);
        if (info != null) {
          if (!processor.execute(info.getElement(), state)) return false;
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
    if (!shouldProcessTraitMember(grType, field, place)) return true;
    if (!processInstanceMember(processInstanceMethods, field) || isSameDeclaration(place, field)) {
      return true;
    }
    LOG.assertTrue(field.getContainingClass() != null);
    final PsiSubstitutor finalSubstitutor =
      PsiClassImplUtil.obtainFinalSubstitutor(field.getContainingClass(), fieldInfo.getSubstitutor(), grType, substitutor, factory, level);

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
    if (!shouldProcessTraitMember(grType, method, place)) return true;
    if (!processInstanceMember(processInstanceMethods, method) ||
        isSameDeclaration(place, method) ||
        !isMethodVisible(placeGroovy, method)) {
      return true;
    }
    LOG.assertTrue(method.getContainingClass() != null);
    final PsiSubstitutor finalSubstitutor =
      PsiClassImplUtil.obtainFinalSubstitutor(method.getContainingClass(), info.getSubstitutor(), grType, substitutor, factory, level);

    return processor.execute(method, state.put(PsiSubstitutor.KEY, finalSubstitutor));
  }

  private static boolean shouldProcessTraitMember(@NotNull GrTypeDefinition grType,
                                                  @NotNull PsiMember element,
                                                  @NotNull PsiElement place) {
    return !grType.isTrait() ||
           !element.hasModifierProperty(PsiModifier.STATIC) ||
           grType.equals(element.getContainingClass()) && PsiTreeUtil.isAncestor(grType, place, true);
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
      List<PsiMethod> result = new ArrayList<>();
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
    ArrayList<PsiMethod> result = new ArrayList<>();
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
    final ArrayList<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<>();

    if (!checkBases) {
      final PsiMethod[] methods = grType.findMethodsByName(name, false);
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
    List<Pair<PsiMethod, PsiSubstitutor>> result = new ArrayList<>();
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
    return getAllFields(grType, true);
  }

  public static PsiField[] getAllFields(GrTypeDefinition grType, boolean includeSynthetic) {
    Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(grType, includeSynthetic);
    return ContainerUtil.map2Array(fieldsMap.values(), PsiField.class, entry -> (PsiField)entry.getElement());
  }

  public static boolean isClassEquivalentTo(GrTypeDefinitionImpl definition, PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(definition, another);
  }

  @NotNull
  public static Collection<? extends PsiMethod> expandReflectedMethods(@NotNull PsiMethod method) {
    if (method instanceof GrMethod) {
      GrReflectedMethod[] methods = ((GrMethod)method).getReflectedMethods();
      if (methods.length > 0) {
        return ContainerUtil.newSmartList(methods);
      }
    }
    return Collections.singletonList(method);
  }

  @NotNull
  public static Set<MethodSignature> getDuplicatedSignatures(@NotNull PsiClass clazz) {
    return CachedValuesManager.getCachedValue(clazz, () -> {
      PsiElementFactory factory = JavaPsiFacade.getInstance(clazz.getProject()).getElementFactory();
      MostlySingularMultiMap<MethodSignature, PsiMethod> signatures = MostlySingularMultiMap.newMap();
      for (PsiMethod method : clazz.getMethods()) {
        MethodSignature signature = method.getSignature(factory.createRawSubstitutor(method));
        signatures.add(signature, method);
      }

      Set<MethodSignature> result = ContainerUtil.newHashSet();
      for (MethodSignature signature : signatures.keySet()) {
        if (signatures.valuesForKey(signature) > 1) {
          result.add(signature);
        }
      }

      return CachedValueProvider.Result.create(result, clazz);
    });
  }

  public static GrAccessorMethod findSetter(GrField field) {
    return CachedValuesManager.getCachedValue(field, () -> CachedValueProvider.Result.create(
      doGetSetter(field), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT
    ));
  }

  @Nullable
  private static GrAccessorMethod doGetSetter(GrField field) {
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return null;
    PsiMethod[] setters = containingClass.findMethodsByName(GroovyPropertyUtils.getSetterName(field.getName()), false);
    for (PsiMethod setter : setters) {
      if (setter instanceof GrAccessorMethod) {
        return (GrAccessorMethod)setter;
      }
    }
    return null;
  }

  public static GrAccessorMethod[] findGetters(GrField field) {
    return CachedValuesManager.getCachedValue(field, () -> CachedValueProvider.Result.create(
      doGetGetters(field), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT
    ));
  }

  @NotNull
  private static GrAccessorMethod[] doGetGetters(GrField field) {
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return GrAccessorMethod.EMPTY_ARRAY;

    GrAccessorMethod getter = null;
    GrAccessorMethod booleanGetter = null;

    PsiMethod[] getters = containingClass.findMethodsByName(GroovyPropertyUtils.getGetterNameNonBoolean(field.getName()), false);
    for (PsiMethod method : getters) {
      if (method instanceof GrAccessorMethod) {
        getter = (GrAccessorMethod)method;
        break;
      }
    }

    PsiMethod[] booleanGetters = containingClass.findMethodsByName(GroovyPropertyUtils.getGetterNameBoolean(field.getName()), false);
    for (PsiMethod method : booleanGetters) {
      if (method instanceof GrAccessorMethod) {
        booleanGetter = (GrAccessorMethod)method;
        break;
      }
    }

    if (getter != null && booleanGetter != null) {
      return new GrAccessorMethod[]{getter, booleanGetter};
    }
    else if (getter != null) {
      return new GrAccessorMethod[]{getter};
    }
    else {
      return GrAccessorMethod.EMPTY_ARRAY;
    }
  }
}
