// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.ast;

import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.HashMap;
import java.util.Map;

public final class LoggingContributor implements AstTransformationSupport {
  private static final Map<String, String> loggers;

  static {
    Map<String, String> map = new HashMap<>(5);
    map.put("groovy.util.logging.Log", "java.util.logging.Logger");
    map.put("groovy.util.logging.Commons", "org.apache.commons.logging.Log");
    map.put("groovy.util.logging.Log4j", "org.apache.log4j.Logger");
    map.put("groovy.util.logging.Log4j2", "org.apache.logging.log4j.core.Logger");
    map.put("groovy.util.logging.Slf4j", "org.slf4j.Logger");
    loggers = map;
  }

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    GrModifierList modifierList = context.getCodeClass().getModifierList();
    if (modifierList == null) return;

    for (GrAnnotation annotation : modifierList.getAnnotations()) {
      String qname = annotation.getQualifiedName();
      String logger = loggers.get(qname);
      if (logger != null) {
        String fieldName = PsiUtil.getAnnoAttributeValue(annotation, "value", "log");
        GrLightField field = new GrLightField(fieldName, logger, context.getCodeClass());
        field.setNavigationElement(annotation);
        field.getModifierList().setModifiers(PsiModifier.PRIVATE, PsiModifier.FINAL, PsiModifier.STATIC);
        field.setOriginInfo("created by @" + annotation.getShortName());
        context.addField(field);
      }
    }
  }
}
