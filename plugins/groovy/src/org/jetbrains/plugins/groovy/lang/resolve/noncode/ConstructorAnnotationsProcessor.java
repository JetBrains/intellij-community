/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.resolve.noncode;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PropertyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.inspections.GroovyImmutableAnnotationInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class ConstructorAnnotationsProcessor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    if (!(qualifierType instanceof PsiClassType)) return;

    PsiClass psiClass = ((PsiClassType)qualifierType).resolve();
    if (!(psiClass instanceof GrTypeDefinition) || psiClass.getName() == null) return;

    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return;

    final PsiAnnotation tupleConstructor = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR);
    final boolean immutable = modifierList.findAnnotation(GroovyImmutableAnnotationInspection.IMMUTABLE) != null ||
                              modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_IMMUTABLE) != null;
    final PsiAnnotation canonical = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_CANONICAL);
    if (!immutable && canonical == null && tupleConstructor == null) {
      return;
    }

    final GrTypeDefinition typeDefinition = (GrTypeDefinition)psiClass;

    if (tupleConstructor != null &&
        typeDefinition.getConstructors().length > 0 &&
        !PsiUtil.getAnnoAttributeValue(tupleConstructor, "force", false)) {
      return;
    }

    final LightMethodBuilder fieldsConstructor = new LightMethodBuilder(psiClass, GroovyFileType.GROOVY_LANGUAGE);
    fieldsConstructor.setConstructor(true);

    Set<String> excludes = new HashSet<String>();
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
        addParametersForSuper(typeDefinition, fieldsConstructor, superFields, superProperties, new HashSet<PsiClass>(), excludes);
      }
    }

    addParameters(typeDefinition, fieldsConstructor,
                  tupleConstructor == null || PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeProperties", true),
                  tupleConstructor != null ? PsiUtil.getAnnoAttributeValue(tupleConstructor, "includeFields", false) : canonical == null,
                  !immutable, excludes);


    if (!processor.execute(fieldsConstructor, state)) return;

    final LightMethodBuilder defaultConstructor = new LightMethodBuilder(psiClass, GroovyFileType.GROOVY_LANGUAGE);
    defaultConstructor.setConstructor(true);
    processor.execute(defaultConstructor, state);
  }

  private static void addParametersForSuper(@NotNull PsiClass typeDefinition,
                                            LightMethodBuilder fieldsConstructor,
                                            boolean superFields,
                                            boolean superProperties, Set<PsiClass> visited, Set<String> excludes) {
    PsiClass parent = typeDefinition.getSuperClass();
    if (parent != null && visited.add(parent) && !GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT.equals(parent.getQualifiedName())) {
      addParametersForSuper(parent, fieldsConstructor, superFields, superProperties, visited, excludes);
      addParameters(parent, fieldsConstructor, superProperties, superFields, true, excludes);
    }
  }

  private static void addParameters(@NotNull PsiClass psiClass,
                                    LightMethodBuilder fieldsConstructor,
                                    boolean includeProperties,
                                    boolean includeFields, boolean optional, Set<String> excludes) {

    if (includeProperties) {
      for (PsiMethod method : psiClass.getMethods()) {
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

    if (includeFields) {
      final Map<String,PsiMethod> properties = PropertyUtil.getAllProperties(psiClass, true, false, false);
      for (PsiField field : psiClass.getFields()) {
        final String name = field.getName();
        if (!excludes.contains(name) && !field.hasModifierProperty(PsiModifier.STATIC) && !properties.containsKey(name)) {
          fieldsConstructor.addParameter(new GrLightParameter(name, field.getType(), fieldsConstructor).setOptional(optional));
        }
      }
    }
  }
}
