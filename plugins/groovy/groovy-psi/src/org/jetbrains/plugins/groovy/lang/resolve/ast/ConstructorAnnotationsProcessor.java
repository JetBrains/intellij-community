/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    final boolean immutable = PsiImplUtil.hasImmutableAnnotation(modifierList);
    final boolean canonical = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_CANONICAL) != null;
    if (!immutable && !canonical && tupleConstructor == null) {
      return;
    }

    if (tupleConstructor != null &&
        typeDefinition.getCodeConstructors().length > 0 &&
        !PsiUtil.getAnnoAttributeValue(tupleConstructor, "force", false)) {
      return;
    }

    final GrLightMethodBuilder fieldsConstructor = generateFieldConstructor(typeDefinition, tupleConstructor, immutable, canonical);
    final GrLightMethodBuilder mapConstructor = generateMapConstructor(typeDefinition);

    context.addMethod(fieldsConstructor);
    context.addMethod(mapConstructor);
  }

  @NotNull
  private static GrLightMethodBuilder generateMapConstructor(@NotNull GrTypeDefinition typeDefinition) {
    final GrLightMethodBuilder mapConstructor = new GrLightMethodBuilder(typeDefinition.getManager(), typeDefinition.getName());
    mapConstructor.addParameter("args", CommonClassNames.JAVA_UTIL_HASH_MAP, false);
    mapConstructor.setConstructor(true);
    mapConstructor.setContainingClass(typeDefinition);
    return mapConstructor;
  }

  @NotNull
  private static GrLightMethodBuilder generateFieldConstructor(@NotNull GrTypeDefinition typeDefinition,
                                                               @Nullable PsiAnnotation tupleConstructor,
                                                               boolean immutable,
                                                               boolean canonical) {
    final GrLightMethodBuilder fieldsConstructor = new GrLightMethodBuilder(typeDefinition.getManager(), typeDefinition.getName());
    fieldsConstructor.setConstructor(true);
    fieldsConstructor.setNavigationElement(typeDefinition);
    fieldsConstructor.setContainingClass(typeDefinition);

    Set<String> excludes = new HashSet<>();
    if (tupleConstructor != null) {
      for (String s : PsiUtil.getAnnoAttributeValue(tupleConstructor, "excludes", "").split(",")) {
        final String name = s.trim();
        if (StringUtil.isNotEmpty(name)) {
          excludes.add(name);
        }
      }
    }


    if (tupleConstructor != null) {
      final boolean superFields = PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeSuperFields", false);
      final boolean superProperties = PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeSuperProperties", false);
      if (superFields || superProperties) {
        addParametersForSuper(typeDefinition, fieldsConstructor, superFields, superProperties, new HashSet<>(), excludes);
      }
    }

    addParameters(typeDefinition, fieldsConstructor,
                  tupleConstructor == null || PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeProperties", true),
                  tupleConstructor != null ? PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeFields", false) : !canonical,
                  !immutable, excludes);

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

  private static void addParametersForSuper(@NotNull PsiClass typeDefinition,
                                            GrLightMethodBuilder fieldsConstructor,
                                            boolean superFields,
                                            boolean superProperties, Set<PsiClass> visited, Set<String> excludes) {
    PsiClass parent = typeDefinition.getSuperClass();
    if (parent != null && visited.add(parent) && !GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(parent.getQualifiedName())) {
      addParametersForSuper(parent, fieldsConstructor, superFields, superProperties, visited, excludes);
      addParameters(parent, fieldsConstructor, superProperties, superFields, true, excludes);
    }
  }

  private static void addParameters(@NotNull PsiClass psiClass,
                                    @NotNull GrLightMethodBuilder fieldsConstructor,
                                    boolean includeProperties,
                                    boolean includeFields,
                                    boolean optional,
                                    @NotNull Set<String> excludes) {

    PsiMethod[] methods = CollectClassMembersUtil.getMethods(psiClass, false);
    if (includeProperties) {
      for (PsiMethod method : methods) {
        if (!method.hasModifierProperty(PsiModifier.STATIC) && PropertyUtil.isSimplePropertySetter(method)) {
          final String name = PropertyUtil.getPropertyNameBySetter(method);
          if (!excludes.contains(name)) {
            final PsiType type = PropertyUtil.getPropertyType(method);
            assert type != null : method;
            fieldsConstructor.addParameter(new GrLightParameter(name, type, fieldsConstructor).setOptional(optional));
          }
        }
      }
    }

    final Map<String,PsiMethod> properties = PropertyUtil.getAllProperties(true, false, methods);
    for (PsiField field : CollectClassMembersUtil.getFields(psiClass, false)) {
      final String name = field.getName();
      if (includeFields ||
          includeProperties && field instanceof GrField && ((GrField)field).isProperty()) {
        if (!excludes.contains(name) && !field.hasModifierProperty(PsiModifier.STATIC) && !properties.containsKey(name)) {
          fieldsConstructor.addParameter(new GrLightParameter(name, field.getType(), fieldsConstructor).setOptional(optional));
        }
      }
    }
  }
}
