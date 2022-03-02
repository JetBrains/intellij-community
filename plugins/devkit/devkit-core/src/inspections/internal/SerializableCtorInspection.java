// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElementKt;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.List;
import java.util.Objects;

public class SerializableCtorInspection extends DevKitUastInspectionBase {

  private static final String PROPERTY_MAPPING_ANNOTATION = "com.intellij.serialization.PropertyMapping";

  public SerializableCtorInspection() {
    super(UClass.class);
  }

  @Override
  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return super.isAllowed(holder) && propertyMappingAnnotationPresent(holder);
  }

  private static boolean propertyMappingAnnotationPresent(@NotNull ProblemsHolder holder) {
    Project project = holder.getProject();
    return JavaPsiFacade.getInstance(project).findClass(PROPERTY_MAPPING_ANNOTATION, holder.getFile().getResolveScope()) != null;
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!InheritanceUtil.isInheritor(aClass.getJavaPsi(), CommonClassNames.JAVA_IO_SERIALIZABLE)) return null;
    if (!hasFieldWithName(aClass, CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME)) return null;
    ProblemsHolder holder = createProblemsHolder(aClass, manager, isOnTheFly);
    for (UMethod constructor : getConstructors(aClass)) {
      if (!isAnnotated(constructor, PROPERTY_MAPPING_ANNOTATION)) {
        PsiElement constructorAnchor = UElementKt.getSourcePsiElement(constructor.getUastAnchor());
        if (constructorAnchor != null) {
          holder.registerProblem(constructorAnchor, DevKitBundle.message("inspection.serializable.constructor.message"),
                                 createFixes(aClass, holder, constructor));
        }
      }
    }
    return holder.getResultsArray();
  }

  private static boolean hasFieldWithName(@NotNull UClass aClass, @NotNull String name) {
    return ContainerUtil.exists(aClass.getFields(), field -> name.equals(field.getName()));
  }

  private static ProblemsHolder createProblemsHolder(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    PsiElement sourcePsi = aClass.getSourcePsi();
    if (sourcePsi != null) {
      return new ProblemsHolder(manager, sourcePsi.getContainingFile(), isOnTheFly);
    }
    throw new IllegalStateException("Could not create problems holder");
  }

  @NotNull
  private static List<UMethod> getConstructors(@NotNull UClass aClass) {
    return ContainerUtil.filter(aClass.getMethods(), method -> method.isConstructor());
  }

  private static boolean isAnnotated(UMethod constructor, String annotationFqn) {
    return ContainerUtil.exists(constructor.getUAnnotations(),
                                annotation -> annotationFqn.equals(annotation.getQualifiedName()));
  }

  @NotNull
  private static LocalQuickFix[] createFixes(@NotNull UClass aClass, ProblemsHolder holder, UMethod constructor) {
    return JavaLanguage.INSTANCE.is(aClass.getLang()) ?
           new LocalQuickFix[]{
             new AddAnnotationPsiFix(PROPERTY_MAPPING_ANNOTATION, constructor.getJavaPsi(),
                                     createExpectedAnnotationAttributes(holder.getProject(), aClass, constructor))}
                                                      : LocalQuickFix.EMPTY_ARRAY;
  }

  private static PsiNameValuePair @NotNull [] createExpectedAnnotationAttributes(Project project, UClass aClass, UMethod constructor) {
    @NonNls StringBuilder builder = new StringBuilder("@PropertyMapping({");
    List<UParameter> parameters = constructor.getUastParameters();
    for (int i = 0; i < parameters.size(); i++) {
      if (i > 0) builder.append(',');
      String name = Objects.requireNonNull(parameters.get(i).getName());
      if (!hasFieldWithName(aClass, name)) {
        name = "my" + StringUtil.capitalize(name);
      }
      if (!hasFieldWithName(aClass, name)) {
        name = "??" + name;
      }
      builder.append('"').append(name).append('"');
    }
    builder.append("})");
    PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
      .createAnnotationFromText(builder.toString(), aClass.getSourcePsi());
    return annotation.getParameterList().getAttributes();
  }
}
