// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import groovy.transform.Undefined;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;
import org.jetbrains.plugins.groovy.transformations.immutable.GrImmutableUtils;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author peter
 */
public class ConstructorAnnotationsProcessor implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    GrTypeDefinition typeDefinition = context.getCodeClass();
    if (typeDefinition.getName() == null) return;
    PsiModifierList modifierList = typeDefinition.getModifierList();
    if (modifierList == null) return;

    final PsiAnnotation tupleConstructor = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR);
    final boolean immutable = GrImmutableUtils.hasImmutableAnnotation(typeDefinition);
    final boolean canonical = modifierList.hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_CANONICAL);
    if (!immutable && !canonical && tupleConstructor == null) {
      return;
    }

    if (tupleConstructor != null &&
        typeDefinition.getCodeConstructors().length > 0 &&
        !PsiUtil.getAnnoAttributeValue(tupleConstructor, "force", false)) {
      return;
    }

    final GrLightMethodBuilder fieldsConstructor = generateFieldConstructor(context, tupleConstructor, immutable, canonical);
    context.addMethod(fieldsConstructor);

    if (tupleConstructor == null || PsiUtil.getAnnoAttributeValue(tupleConstructor, "defaults", true)) {
      GrLightMethodBuilder mapConstructor = generateMapConstructor(typeDefinition);
      context.addMethod(mapConstructor);
    }
  }

  @NotNull
  private static GrLightMethodBuilder generateMapConstructor(@NotNull GrTypeDefinition typeDefinition) {
    final GrLightMethodBuilder mapConstructor = new GrLightMethodBuilder(typeDefinition.getManager(), typeDefinition.getName());
    mapConstructor.addParameter("args", CommonClassNames.JAVA_UTIL_HASH_MAP);
    mapConstructor.setConstructor(true);
    mapConstructor.setContainingClass(typeDefinition);
    return mapConstructor;
  }

  private static @Nullable List<@NotNull String> getIdentifierList(@NotNull PsiAnnotation annotation, @NotNull String attributeName) {
    if (!annotation.hasAttribute(attributeName)) {
      return null;
    }
    String rawIdentifiers = GrAnnotationUtil.inferStringAttribute(annotation, attributeName);
    if (rawIdentifiers != null) {
      return Arrays.asList(rawIdentifiers.split(","));
    }
    return GrAnnotationUtil.getStringArrayValue(annotation, attributeName, false);
  }

  private static boolean isInternalName(@NotNull String name) {
    return name.contains("$");
  }

  private static @NotNull Pair<Predicate<? super String>, @Nullable List<String>> collectNamesOrderInformation(@NotNull PsiAnnotation tupleConstructor) {
    Boolean allNames = GrAnnotationUtil.inferBooleanAttribute(tupleConstructor, "allNames");
    boolean allowInternalNames = Boolean.TRUE.equals(allNames);

    Set<String> excludes = new HashSet<>();
    List<String> excludesList = getIdentifierList(tupleConstructor, "excludes");
    for (String s : (excludesList == null ? Collections.<String>emptyList() : excludesList)) {
      final String name = s.trim();
      if (StringUtil.isEmpty(name)) continue;
      excludes.add(name);
    }

    List<String> includes;
    List<String> includesList = getIdentifierList(tupleConstructor, "includes");
    if (includesList == null || (includesList.size() == 1 && Undefined.isUndefined(includesList.get(0)))) {
      includes = null;
    }
    else {
      includes = new ArrayList<>();
      for (String includedField : includesList) {
        String name = includedField.trim();
        if (StringUtil.isEmpty(name)) continue;
        includes.add(name);
      }
    }

    Predicate<? super String> filter = name -> {
      boolean internalFilter = allowInternalNames || !isInternalName(name);
      boolean excludesFilter = !excludes.contains(name);
      boolean includesFilter = includes == null || includes.contains(name);
      return internalFilter && excludesFilter && includesFilter;
    };
    return Pair.create(filter, includes);
  }

  @NotNull
  private static GrLightMethodBuilder generateFieldConstructor(@NotNull TransformationContext context,
                                                               @Nullable PsiAnnotation tupleConstructor,
                                                               boolean immutable,
                                                               boolean canonical) {
    final GrTypeDefinition typeDefinition = context.getCodeClass();
    final GrLightMethodBuilder fieldsConstructor = new GrLightMethodBuilder(typeDefinition.getManager(), typeDefinition.getName());
    fieldsConstructor.setConstructor(true);
    fieldsConstructor.setNavigationElement(typeDefinition);
    fieldsConstructor.setContainingClass(typeDefinition);

    Pair<Predicate<? super String>, List<String>> namesOrder =
      tupleConstructor == null ? null : collectNamesOrderInformation(tupleConstructor);
    Predicate<? super String> filter = namesOrder == null ? name -> true : namesOrder.first;
    List<String> includes = namesOrder == null ? null : namesOrder.second;

    boolean isOptional;
    if (tupleConstructor != null) {
      isOptional = !immutable && PsiUtil.getAnnoAttributeValue(tupleConstructor, "defaults", true);
    }
    else {
      isOptional = !immutable;
    }

    List<GrParameter> parameterCollector = new ArrayList<>();
    if (tupleConstructor != null) {
      final boolean superFields = PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeSuperFields", false);
      final boolean superProperties = PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeSuperProperties", false);
      if (superFields || superProperties) {
        PsiClass superClass = context.getHierarchyView().getSuperClass();
        addParametersForSuper(superClass, parameterCollector, fieldsConstructor, isOptional, superFields, superProperties, new HashSet<>(),
                              filter);
      }
    }

    boolean includeProperties = tupleConstructor == null || PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeProperties", true);
    boolean includeFields = tupleConstructor != null ? PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeFields", false) : !canonical;
    addParameters(typeDefinition, parameterCollector, fieldsConstructor, includeProperties, includeFields, isOptional, filter);

    if (includes != null) {
      Comparator<GrParameter> includeComparator = Comparator.comparingInt(param -> includes.indexOf(param.getName()));
      parameterCollector.sort(includeComparator);
    }

    for (GrParameter parameter : parameterCollector) {
      fieldsConstructor.addParameter(parameter);
    }

    if (immutable) {
      fieldsConstructor.setOriginInfo("created by @Immutable");
    }
    else if (tupleConstructor != null) {
      fieldsConstructor.setOriginInfo("created by @TupleConstructor");
    }
    else /*if (canonical != null)*/ {
      fieldsConstructor.setOriginInfo("created by @Canonical");
    }
    return fieldsConstructor;
  }

  private static void addParametersForSuper(@Nullable PsiClass typeDefinition,
                                            @NotNull List<@NotNull GrParameter> collector,
                                            @NotNull GrLightMethodBuilder builder,
                                            boolean isOptional,
                                            boolean superFields,
                                            boolean superProperties,
                                            Set<? super PsiClass> visited,
                                            Predicate<? super String> nameFilter) {
    if (typeDefinition == null) {
      return;
    }
    if (!visited.add(typeDefinition) || GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(typeDefinition.getQualifiedName())) {
      return;
    }
    addParametersForSuper(typeDefinition.getSuperClass(), collector, builder, isOptional, superFields, superProperties, visited,
                          nameFilter);
    addParameters(typeDefinition, collector, builder, superProperties, superFields, isOptional, nameFilter);
  }

  private static void addParameters(@NotNull PsiClass psiClass,
                                    @NotNull List<@NotNull GrParameter> collector,
                                    @NotNull GrLightMethodBuilder builder,
                                    boolean includeProperties,
                                    boolean includeFields,
                                    boolean optional,
                                    @NotNull Predicate<? super String> nameFilter) {

    PsiMethod[] methods = CollectClassMembersUtil.getMethods(psiClass, false);
    if (includeProperties) {
      for (PsiMethod method : methods) {
        if (!method.hasModifierProperty(PsiModifier.STATIC) && PropertyUtilBase.isSimplePropertySetter(method)) {
          final String name = PropertyUtilBase.getPropertyNameBySetter(method);
          if (!nameFilter.test(name)) continue;
          final PsiType type = PropertyUtilBase.getPropertyType(method);
          assert type != null : method;
          collector.add(new GrLightParameter(name, type, builder).setOptional(optional));
        }
      }
    }

    final Map<String,PsiMethod> properties = PropertyUtilBase.getAllProperties(true, false, methods);
    for (PsiField field : CollectClassMembersUtil.getFields(psiClass, false)) {
      final String name = field.getName();
      if (includeFields ||
          includeProperties && field instanceof GrField && ((GrField)field).isProperty()) {
        if (nameFilter.test(name) && !field.hasModifierProperty(PsiModifier.STATIC) && !properties.containsKey(name)) {
          collector.add(new GrLightParameter(name, field.getType(), builder).setOptional(optional));
        }
      }
    }
  }
}
