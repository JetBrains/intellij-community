// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionBase;

import java.util.Objects;

public class SerializableCtorInspection extends DevKitInspectionBase {
  private static final String PROPERTY_MAPPING_ANNOTATION = "com.intellij.serialization.PropertyMapping";

  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (JavaPsiFacade.getInstance(holder.getProject()).findClass(PROPERTY_MAPPING_ANNOTATION, holder.getFile().getResolveScope()) == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        if (!InheritanceUtil.isInheritor(aClass, "java.io.Serializable"))
          return;
        if (aClass.findFieldByName(CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME, false) == null)
          return;
        PsiMethod[] constructors = aClass.getConstructors();
        for (PsiMethod constructor : constructors) {
          if (constructor.getNameIdentifier() != null && constructor.getAnnotation(PROPERTY_MAPPING_ANNOTATION) == null) {
            @NonNls StringBuilder builder = new StringBuilder("@PropertyMapping({");
            JvmParameter[] parameters = constructor.getParameters();
            for (int i = 0; i < parameters.length; i++) {
              if (i > 0) builder.append(',');
              String name = Objects.requireNonNull(parameters[i].getName());
              if (aClass.findFieldByName(name, false) == null) {
                name = "my" + StringUtil.capitalize(name);
              }
              if (aClass.findFieldByName(name, false) == null) {
                name = "??" + name;
              }
              builder.append('"').append(name).append('"');
            }
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(aClass.getProject())
              .createAnnotationFromText(builder.append("})").toString(), aClass);
            holder.registerProblem(constructor.getNameIdentifier(), DevKitBundle.message("inspection.serializable.constructor.message"),
                                   new AddAnnotationPsiFix(PROPERTY_MAPPING_ANNOTATION, constructor, annotation.getParameterList().getAttributes()));
          }
        }
      }
    };
  }
}
