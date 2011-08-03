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

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.GrTypeDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrClassImplUtil {
  private static final Condition<PsiClassType> IS_GROOVY_OBJECT = new Condition<PsiClassType>() {
    public boolean value(PsiClassType psiClassType) {
      return TypesUtil.isClassType(psiClassType, GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME);
    }
  };

  private GrClassImplUtil() {
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
    if (grType.isInterface() /*|| extendsTypes.length > 0*/) return extendsTypes;
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
    Set<PsiClass> visited = new com.intellij.util.containers.hash.HashSet<PsiClass>();
    List<PsiClassType> result = new ArrayList<PsiClassType>();
    getImplementListsInner(grType, result, visited);
    return result.toArray(new PsiClassType[result.size()]);
  }

  private static void getImplementListsInner(GrTypeDefinition grType, List<PsiClassType> result, Set<PsiClass> visited) {
    if (!visited.add(grType)) return;

    final PsiClassType[] implementsTypes = getReferenceListTypes(grType.getImplementsClause());
    List<PsiClassType> fromDelegates = getImplementsFromDelegate(grType, visited);
    if (fromDelegates != null) {
      result.addAll(fromDelegates);
    }
    result.addAll(Arrays.asList(implementsTypes));

    if (!grType.isInterface() &&
        !ContainerUtil.or(implementsTypes, IS_GROOVY_OBJECT) &&
        !ContainerUtil.or(getReferenceListTypes(grType.getExtendsClause()), IS_GROOVY_OBJECT)) {
      result.add(getGroovyObjectType(grType));
    }
  }

  @Nullable
  private static List<PsiClassType> getImplementsFromDelegate(final GrTypeDefinition grType, final Set<PsiClass> visited) {
    return RecursionManager.createGuard("groovyDelegateFields").doPreventingRecursion(grType, new Computable<List<PsiClassType>>() {
      @Override
      public List<PsiClassType> compute() {
        List<PsiClassType> result = new ArrayList<PsiClassType>();
        final GrField[] fields = grType.getFields();
        for (GrField field : fields) {
          final PsiAnnotation delegate = getAnnotation(field, GroovyCommonClassNames.GROOVY_LANG_DELEGATE);
          if (delegate == null) continue;

          final boolean shouldImplement = shouldImplementDelegatedInterfaces(delegate);
          if (!shouldImplement) continue;

          final PsiType type = field.getDeclaredType();
          if (!(type instanceof PsiClassType)) continue;

          final PsiClass psiClass = ((PsiClassType)type).resolve();
          if (psiClass == null) continue;

          if (psiClass instanceof GrTypeDefinition) {
            getImplementListsInner((GrTypeDefinition)psiClass, result, visited);
          }
          else {
            result.addAll(Arrays.asList(psiClass.getImplementsListTypes()));
          }
          if (psiClass.isInterface()) {
            result.add((PsiClassType)type);
          }
        }
        return result;

      }
    });
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
  public static PsiMethod[] getAllMethods(GrTypeDefinition grType) {
    List<PsiMethod> allMethods = new ArrayList<PsiMethod>();
    getAllMethodsInner(grType, allMethods, new HashSet<PsiClass>());

    return allMethods.toArray(new PsiMethod[allMethods.size()]);
  }

  private static void getAllMethodsInner(PsiClass clazz, List<PsiMethod> allMethods, HashSet<PsiClass> visited) {
    if (visited.contains(clazz)) return;
    visited.add(clazz);

    ContainerUtil.addAll(allMethods, clazz.getMethods());

    final PsiField[] fields = clazz.getFields();
    for (PsiField field : fields) {
      if (field instanceof GrField) {
        final GrField groovyField = (GrField)field;
        if (groovyField.isProperty()) {
          PsiMethod[] getters = groovyField.getGetters();
          if (getters.length > 0) ContainerUtil.addAll(allMethods, getters);
          PsiMethod setter = groovyField.getSetter();
          if (setter != null) allMethods.add(setter);
        }
      }
    }

    final PsiClass[] supers = clazz.getSupers();
    for (PsiClass aSuper : supers) {
      getAllMethodsInner(aSuper, allMethods, visited);
    }
  }

  private static PsiClassType[] getReferenceListTypes(@Nullable GrReferenceList list) {
    if (list == null) return PsiClassType.EMPTY_ARRAY;
    return list.getReferenceTypes();
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
                                            PsiElement lastParent,
                                            @NotNull PsiElement place) {
    for (final PsiTypeParameter typeParameter : grType.getTypeParameters()) {
      if (!ResolveUtil.processElement(processor, typeParameter, state)) return false;
    }

    NameHint nameHint = processor.getHint(NameHint.KEY);
    //todo [DIANA] look more carefully
    String name = nameHint == null ? null : nameHint.getName(state);
    ClassHint classHint = processor.getHint(ClassHint.KEY);
    final PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getProject());

    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY)) {
      Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(grType);
      if (name != null) {
        CandidateInfo fieldInfo = fieldsMap.get(name);
        if (fieldInfo != null) {
          final PsiField field = (PsiField)fieldInfo.getElement();
          if (!isSameDeclaration(place, field)) { //the same variable declaration
            final PsiSubstitutor finalSubstitutor = PsiClassImplUtil
              .obtainFinalSubstitutor(field.getContainingClass(), fieldInfo.getSubstitutor(), grType, substitutor, place, factory);
            if (!processor.execute(field, state.put(PsiSubstitutor.KEY, finalSubstitutor))) return false;
          }
        }
      }
      else {
        for (CandidateInfo info : fieldsMap.values()) {
          final PsiField field = (PsiField)info.getElement();
          if (!isSameDeclaration(place, field)) {  //the same variable declaration
            final PsiSubstitutor finalSubstitutor = PsiClassImplUtil
              .obtainFinalSubstitutor(field.getContainingClass(), info.getSubstitutor(), grType, substitutor, place, factory);
            if (!processor.execute(field, state.put(PsiSubstitutor.KEY, finalSubstitutor))) return false;
          }
        }
      }
    }

    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.METHOD)) {
      Map<String, List<CandidateInfo>> methodsMap = CollectClassMembersUtil.getAllMethods(grType, true);
      boolean isPlaceGroovy = place.getLanguage() == GroovyFileType.GROOVY_LANGUAGE;
      if (name == null) {
        for (List<CandidateInfo> list : methodsMap.values()) {
          for (CandidateInfo info : list) {
            PsiMethod method = (PsiMethod)info.getElement();
            if (!isSameDeclaration(place, method) && isMethodVisible(isPlaceGroovy, method)) {
              final PsiSubstitutor finalSubstitutor = PsiClassImplUtil
                .obtainFinalSubstitutor(method.getContainingClass(), info.getSubstitutor(), grType, substitutor, place, factory);
              if (!processor.execute(method, state.put(PsiSubstitutor.KEY, finalSubstitutor))) {
                return false;
              }
            }
          }
        }
      }
      else {
        List<CandidateInfo> byName = methodsMap.get(name);
        if (byName != null) {
          for (CandidateInfo info : byName) {
            PsiMethod method = (PsiMethod)info.getElement();
            if (!isSameDeclaration(place, method) && isMethodVisible(isPlaceGroovy, method)) {
              final PsiSubstitutor finalSubstitutor = PsiClassImplUtil
                .obtainFinalSubstitutor(method.getContainingClass(), info.getSubstitutor(), grType, substitutor, place, factory);
              if (!processor.execute(method, state.put(PsiSubstitutor.KEY, finalSubstitutor))) {
                return false;
              }
            }
          }
        }
      }
    }

    final GrTypeDefinitionBody body = grType.getBody();
    if (body != null && !isSuperClassReferenceResolving(grType, lastParent)) {
      if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.CLASS)) {
        for (CandidateInfo info : CollectClassMembersUtil.getAllInnerClasses(grType, false).values()) {
          final PsiClass innerClass = (PsiClass)info.getElement();
          assert innerClass != null;
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

  private static boolean isSuperClassReferenceResolving(GrTypeDefinition grType, PsiElement lastParent) {
    return lastParent instanceof GrReferenceList ||
           grType.isAnonymous() && lastParent == ((GrAnonymousClassDefinition)grType).getBaseClassReferenceGroovy();
  }


  private static boolean isSameDeclaration(PsiElement place, PsiElement element) {
    if (element instanceof GrAccessorMethod) element = ((GrAccessorMethod)element).getProperty();

    if (!(element instanceof GrField)) return false;
    while (place != null) {
      place = place.getParent();
      if (place == element) return true;
      if (place instanceof GrClosableBlock) return false;
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
      final PsiClass clazz = method.getContainingClass();
      if (clazz == null) continue;
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(clazz, grType, PsiSubstitutor.EMPTY);
      if (superSubstitutor == null) continue;
      final MethodSignature signature = method.getSignature(superSubstitutor);
      if (signature.equals(patternSignature)) return method;
    }

    return null;
  }

  private static PsiMethod[] findMethodsByName(GrTypeDefinition grType,
                                               String name,
                                               boolean checkBases,
                                               boolean includeSyntheticAccessors) {
    if (!checkBases) {
      List<PsiMethod> result = new ArrayList<PsiMethod>();
      for (PsiMethod method : includeSyntheticAccessors ? grType.getMethods() : grType.getGroovyMethods()) {
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
      final PsiClass clazz = method.getContainingClass();
      if (clazz == null) continue;
      PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(clazz, grType, PsiSubstitutor.EMPTY);
      if (superSubstitutor == null) continue;

      final MethodSignature signature = method.getSignature(superSubstitutor);
      if (signature.equals(patternSignature)) //noinspection unchecked
      {
        result.add(method);
      }
    }
    return result.toArray(new PsiMethod[result.size()]);
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
        result.add(new Pair<PsiMethod, PsiSubstitutor>(method, PsiSubstitutor.EMPTY));
      }
    }
    else {
      final Map<String, List<CandidateInfo>> map = CollectClassMembersUtil.getAllMethods(grType, true);
      final List<CandidateInfo> candidateInfos = map.get(name);
      if (candidateInfos != null) {
        for (CandidateInfo info : candidateInfos) {
          final PsiElement element = info.getElement();
          result.add(new Pair<PsiMethod, PsiSubstitutor>((PsiMethod)element, info.getSubstitutor()));
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
        result.add(new Pair<PsiMethod, PsiSubstitutor>((PsiMethod)info.getElement(), info.getSubstitutor()));
      }
    }

    return result;
  }

  @Nullable
  public static PsiField findFieldByName(GrTypeDefinition grType, String name, boolean checkBases) {
    if (!checkBases) {
      for (GrField field : grType.getFields()) {
        if (name.equals(field.getName())) return field;
      }

      return null;
    }

    Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(grType);
    final CandidateInfo info = fieldsMap.get(name);
    return info == null ? null : (PsiField)info.getElement();
  }

  public static PsiField[] getAllFields(GrTypeDefinition grType) {
    Map<String, CandidateInfo> fieldsMap = CollectClassMembersUtil.getAllFields(grType);
    return ContainerUtil.map2Array(fieldsMap.values(), PsiField.class, new Function<CandidateInfo, PsiField>() {
      public PsiField fun(CandidateInfo entry) {
        return (PsiField)entry.getElement();
      }
    });
  }

  public static boolean isClassEquivalentTo(GrTypeDefinitionImpl definition, PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(definition, another);
  }

  private static final Key<CachedValue<Collection<PsiMethod>>> DELEGATE_METHOD_CACHE_KEY = Key.create("delegate method cache");

  public static Collection<PsiMethod> getDelegatedMethods(final GrTypeDefinition clazz) {
    CachedValue<Collection<PsiMethod>> cachedValue = clazz.getUserData(DELEGATE_METHOD_CACHE_KEY);
    if (cachedValue == null) {
      final CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(clazz.getProject());
      cachedValue = cachedValuesManager.createCachedValue(new CachedValueProvider<Collection<PsiMethod>>() {
        @Override
        public Result<Collection<PsiMethod>> compute() {
          List<PsiMethod> result = new ArrayList<PsiMethod>();
          final GrField[] fields = clazz.getFields();
          for (GrField field : fields) {
            final PsiAnnotation delegate = getAnnotation(field, GroovyCommonClassNames.GROOVY_LANG_DELEGATE);
            if (delegate == null) continue;

            final PsiType type = field.getDeclaredType();
            if (!(type instanceof PsiClassType)) continue;

            final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
            final PsiClass psiClass = resolveResult.getElement();
            if (psiClass == null) continue;

            final boolean deprecated = shouldDelegateDeprecated(delegate);
            final List<PsiMethod> methods;
            if (psiClass instanceof GrTypeDefinition) {
              methods = ((GrTypeDefinition)psiClass).getBody().getMethods();
            }
            else {
              methods = Arrays.asList(psiClass.getMethods());
            }
            for (PsiMethod method : methods) {
              if (method.isConstructor()) continue;
              if (!deprecated && getAnnotation(method, "java.lang.Deprecated") != null) continue;
              result.add(generateDelegateMethod(method, clazz, resolveResult.getSubstitutor()));
            }
          }
          return new Result<Collection<PsiMethod>>(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        }
      }, false);
      clazz.putUserData(DELEGATE_METHOD_CACHE_KEY, cachedValue);
    }
    return cachedValue.getValue();
  }

  private static PsiMethod generateDelegateMethod(PsiMethod method, PsiClass clazz, PsiSubstitutor substitutor) {
    final LightMethodBuilder builder = new LightMethodBuilder(clazz.getManager(), GroovyFileType.GROOVY_LANGUAGE, method.getName());
    builder.setContainingClass(clazz);
    builder.setReturnType(substitutor.substitute(method.getReturnType()));
    builder.setNavigationElement(method);
    builder.addModifier(PsiModifier.PUBLIC);
    final PsiParameter[] originalParameters = method.getParameterList().getParameters();

    final PsiClass containingClass = method.getContainingClass();
    boolean isRaw = containingClass != null && PsiUtil.isRawSubstitutor(containingClass, substitutor);
    if (isRaw) {
      PsiTypeParameter[] methodTypeParameters = method.getTypeParameters();
      substitutor = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createRawSubstitutor(substitutor, methodTypeParameters);
    }

    for (int i = 0, originalParametersLength = originalParameters.length; i < originalParametersLength; i++) {
      PsiParameter originalParameter = originalParameters[i];
      PsiType type;
      if (isRaw) {
        type = TypeConversionUtil.erasure(substitutor.substitute(originalParameter.getType()));
      }
      else {
        type = substitutor.substitute(originalParameter.getType());
      }
      if (type == null) {
        type = PsiType.getJavaLangObject(clazz.getManager(), clazz.getResolveScope());
      }
      builder.addParameter(StringUtil.notNullize(originalParameter.getName(), "p" + i), type);
    }
    builder.setBaseIcon(GroovyIcons.METHOD);
    return builder;
  }

  @Nullable
  private static PsiAnnotation getAnnotation(@NotNull PsiModifierListOwner field, @NotNull String annotationName) {
    final PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) return null;
    return modifierList.findAnnotation(annotationName);
  }

  private static boolean shouldDelegateDeprecated(PsiAnnotation delegate) {
    final PsiAnnotationParameterList parameterList = delegate.getParameterList();
    final PsiNameValuePair[] attributes = parameterList.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      final String name = attribute.getName();
      if ("deprecated".equals(name)) {
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (value instanceof GrLiteral) {
          final Object innerValue = ((GrLiteral)value).getValue();
          if (innerValue instanceof Boolean) {
            return (Boolean)innerValue;
          }
        }
      }
    }
    return false;
  }

  private static boolean shouldImplementDelegatedInterfaces(PsiAnnotation delegate) {
    final PsiAnnotationParameterList parameterList = delegate.getParameterList();
    final PsiNameValuePair[] attributes = parameterList.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      final String name = attribute.getName();
      if ("interfaces".equals(name)) {
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (value instanceof GrLiteral) {
          final Object innerValue = ((GrLiteral)value).getValue();
          if (innerValue instanceof Boolean) {
            return (Boolean)innerValue;
          }
        }
      }
    }
    return true;
  }

}
