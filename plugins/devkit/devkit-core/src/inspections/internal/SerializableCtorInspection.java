// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.*;
import com.intellij.lang.jvm.actions.AnnotationAttributeRequest;
import com.intellij.lang.jvm.actions.AnnotationAttributeValueRequestKt;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitInspectionUtil;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;

import java.util.List;
import java.util.Objects;

import static com.intellij.lang.jvm.actions.AnnotationRequestsKt.annotationRequest;

@ApiStatus.Internal
public final class SerializableCtorInspection extends DevKitUastInspectionBase {

  private static final String PROPERTY_MAPPING_ANNOTATION = "com.intellij.serialization.PropertyMapping";

  @VisibleForTesting
  public SerializableCtorInspection() {
    super(UClass.class);
  }

  @Override
  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return super.isAllowed(holder) &&
           DevKitInspectionUtil.isClassAvailable(holder, PROPERTY_MAPPING_ANNOTATION);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!InheritanceUtil.isInheritor(aClass.getJavaPsi(), CommonClassNames.JAVA_IO_SERIALIZABLE)) return null;
    if (!hasFieldWithName(aClass, CommonClassNames.SERIAL_VERSION_UID_FIELD_NAME)) return null;
    ProblemsHolder holder = createProblemsHolder(aClass, manager, isOnTheFly);
    for (UMethod constructor : getConstructors(aClass)) {
      if (!isAnnotatedWithPropertyMapping(constructor)) {
        ProblemHolderUtilKt.registerUProblem(holder, constructor,
                                             DevKitBundle.message("inspection.serializable.constructor.message"),
                                             createFixes(aClass, holder, constructor));
      }
    }
    return holder.getResultsArray();
  }

  private static boolean hasFieldWithName(@NotNull UClass aClass, @NotNull String name) {
    return ContainerUtil.exists(aClass.getFields(), field -> name.equals(field.getName()));
  }

  private static @NotNull @Unmodifiable List<UMethod> getConstructors(@NotNull UClass aClass) {
    return ContainerUtil.filter(aClass.getMethods(), method -> method.isConstructor());
  }

  private static boolean isAnnotatedWithPropertyMapping(UMethod constructor) {
    return ContainerUtil.exists(constructor.getUAnnotations(),
                                annotation -> PROPERTY_MAPPING_ANNOTATION.equals(annotation.getQualifiedName()));
  }

  private static LocalQuickFix[] createFixes(@NotNull UClass aClass, ProblemsHolder holder, UMethod constructor) {
    return IntentionWrapper.wrapToQuickFixes(
        JvmElementActionFactories.createAddAnnotationActions(
          constructor.getJavaPsi(),
          annotationRequest(PROPERTY_MAPPING_ANNOTATION, createExpectedAnnotationAttributes(holder.getProject(), aClass, constructor))),
        Objects.requireNonNull(aClass.getSourcePsi()).getContainingFile())
      .toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static AnnotationAttributeRequest @NotNull [] createExpectedAnnotationAttributes(Project project,
                                                                                           UClass aClass,
                                                                                           UMethod constructor) {
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
    return AnnotationAttributeValueRequestKt.attributeRequests(annotation).toArray(AnnotationAttributeRequest[]::new);
  }
}
