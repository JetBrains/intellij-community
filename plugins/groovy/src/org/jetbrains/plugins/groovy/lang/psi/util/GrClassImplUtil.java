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

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.TypeConversionUtil;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
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
      return psiClassType.equalsToText(GrTypeDefinition.DEFAULT_BASE_CLASS_NAME);
    }
  };
  public static final String SYNTHETIC_METHOD_IMPLEMENTATION = "GroovySyntheticMethodImplementation";

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
    final List<PsiClassType> extendsTypes = getReferenceListTypes(grType.getExtendsClause());
    return extendsTypes.toArray(new PsiClassType[extendsTypes.size()]);
  }

  @NotNull
  public static PsiClassType[] getImplementsListTypes(GrTypeDefinition grType) {
    final List<PsiClassType> implementsTypes = getReferenceListTypes(grType.getImplementsClause());
    if (!grType.isInterface() &&
        !ContainerUtil.or(implementsTypes, IS_GROOVY_OBJECT) &&
        !ContainerUtil.or(getReferenceListTypes(grType.getExtendsClause()), IS_GROOVY_OBJECT)) {
      implementsTypes.add(getGroovyObjectType(grType));
    }
    return implementsTypes.toArray(new PsiClassType[implementsTypes.size()]);
  }

  private static PsiClassType getGroovyObjectType(GrTypeDefinition grType) {
    return JavaPsiFacade.getInstance(grType.getProject()).getElementFactory()
      .createTypeByFQClassName(GrTypeDefinition.DEFAULT_BASE_CLASS_NAME, grType.getResolveScope());
  }

  @NotNull
  public static PsiClassType[] getSuperTypes(GrTypeDefinition grType) {
    PsiClassType[] extendsList = grType.getExtendsListTypes();
    if (extendsList.length == 0) {
      extendsList = new PsiClassType[]{createBaseClassType(grType)};
    }

    return ArrayUtil.mergeArrays(extendsList, grType.getImplementsListTypes(), PsiClassType.class);
  }

  public static PsiClassType createBaseClassType(GrTypeDefinition grType) {
    if (grType.isEnum()) {
      return JavaPsiFacade.getInstance(grType.getProject()).getElementFactory()
        .createTypeByFQClassName(CommonClassNames.JAVA_LANG_ENUM, grType.getResolveScope());
    }
    else {
      return JavaPsiFacade.getInstance(grType.getProject()).getElementFactory()
        .createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, grType.getResolveScope());
    }
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

    allMethods.addAll(Arrays.asList(clazz.getMethods()));

    final PsiField[] fields = clazz.getFields();
    for (PsiField field : fields) {
      if (field instanceof GrField) {
        final GrField groovyField = (GrField)field;
        if (groovyField.isProperty()) {
          PsiMethod[] getters = groovyField.getGetters();
          if (getters.length > 0) allMethods.addAll(Arrays.asList(getters));
          PsiMethod setter = groovyField.getSetter();
          if (setter != null) allMethods.add(setter);
        }
      }
    }

    final PsiClass[] supers = clazz.getSupers();
    if (supers.length < 2) {
      addGroovyObjectMethods(clazz, allMethods);
    }
    for (PsiClass aSuper : supers) {
      getAllMethodsInner(aSuper, allMethods, visited);
    }
  }

  public static void addGroovyObjectMethods(PsiClass clazz, List<PsiMethod> allMethods) {
    if (clazz instanceof GrTypeDefinition && !clazz.isInterface() /*&& clazz.getExtendsListTypes().length == 0*/) {
      final PsiClass groovyObject =
        JavaPsiFacade.getInstance(clazz.getProject()).findClass(GrTypeDefinition.DEFAULT_BASE_CLASS_NAME, clazz.getResolveScope());
      if (groovyObject != null) {
        for (final PsiMethod method : groovyObject.getMethods()) {
          allMethods.add(createSyntheticMethodImplementation(clazz, method));
        }
      }
    }
  }

  private static LightMethodBuilder createSyntheticMethodImplementation(PsiClass containingClass, PsiMethod interfaceMethod) {
    final LightMethodBuilder result =
      new LightMethodBuilder(interfaceMethod.getManager(), GroovyFileType.GROOVY_LANGUAGE, interfaceMethod.getName()).
        setContainingClass(containingClass).
        setNavigationElement(interfaceMethod).
        setReturnType(interfaceMethod.getReturnType()).
        setModifiers(PsiModifier.PUBLIC).
        setBaseIcon(GroovyIcons.METHOD).
        setMethodKind(SYNTHETIC_METHOD_IMPLEMENTATION);
    for (PsiParameter psiParameter : interfaceMethod.getParameterList().getParameters()) {
      result.addParameter(psiParameter);
    }
    return result;
  }


  private static List<PsiClassType> getReferenceListTypes(@Nullable GrReferenceList list) {
    final ArrayList<PsiClassType> types = new ArrayList<PsiClassType>();
    if (list != null) {
      for (GrCodeReferenceElement ref : list.getReferenceElements()) {
        types.add(new GrClassReferenceType(ref));
      }
    }
    return types;
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
      boolean isPlaceGroovy = place.getLanguage() == GroovyFileType.GROOVY_FILE_TYPE.getLanguage();
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
}
