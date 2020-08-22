// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UTypeReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.List;
import java.util.Map;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING_SHORT;
import static com.intellij.psi.util.InheritanceUtil.isInheritorOrSelf;
import static com.intellij.psi.util.PsiTypesUtil.getPsiClass;
import static com.intellij.util.ArrayUtil.getFirstElement;
import static com.intellij.util.containers.ContainerUtil.exists;
import static java.util.Arrays.asList;

public final class LeakableMapKeyInspection extends DevKitUastInspectionBase {

  @Override
  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return DevKitInspectionBase.isAllowedInPluginsOnly(holder);
  }

  @Override
  protected @NotNull PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(holder.getProject());
    PsiFile file = holder.getFile();
    GlobalSearchScope resolveScope = file.getResolveScope();

    PsiClass languageClass = psiFacade.findClass(Language.class.getName(), resolveScope);
    PsiClass fileTypeClass = psiFacade.findClass(FileType.class.getName(), resolveScope);
    PsiClass mapClass = psiFacade.findClass(Map.class.getName(), resolveScope);

    if (languageClass == null ||
        fileTypeClass == null ||
        mapClass == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    UFieldVisitor visitor = new UFieldVisitor(
      holder,
      mapClass,
      asList(languageClass, fileTypeClass)
    );

    //noinspection unchecked
    return UastHintedVisitorAdapter.create(
      file.getLanguage(),
      visitor,
      new Class[]{UField.class}
    );
  }

  private static final class UFieldVisitor extends AbstractUastNonRecursiveVisitor {

    private final @NotNull ProblemsHolder myHolder;
    private final @NotNull PsiClassType myMapType;
    private final @NotNull List<? extends PsiClass> myBaseClasses;

    private UFieldVisitor(@NotNull ProblemsHolder holder,
                          @NotNull PsiClass mapClass,
                          @NotNull List<? extends PsiClass> classes) {
      myHolder = holder;
      myMapType = PsiElementFactory.getInstance(holder.getProject()).createType(mapClass);
      myBaseClasses = classes;
    }

    @Override
    public boolean visitField(@NotNull UField field) {
      PsiType fieldType = field.getType();

      if (myMapType.isAssignableFrom(fieldType)) {
        PsiClass keyClass = getKeyClass(fieldType);

        if (keyClass != null && isLeakable(keyClass)) {
          PsiElement typeElement = getTypeElement(field);

          //noinspection UElementAsPsi
          PsiElement element = typeElement == null ?
                               field.getNameIdentifier() :
                               typeElement;

          myHolder.registerProblem(
            element,
            DevKitBundle.message("inspections.leakable.map.key.text", keyClass.getName()),
            ReplaceKeyQuickFix.createFixesFor(element)
          );
        }
      }

      return true;
    }

    private boolean isLeakable(@Nullable PsiClass keyClass) {
      return exists(
        myBaseClasses,
        baseClass -> isInheritorOrSelf(keyClass, baseClass, true)
      );
    }

    private static @Nullable PsiClass getKeyClass(@NotNull PsiType fieldType) {
      PsiType[] parameters = fieldType instanceof PsiClassType ?
                             ((PsiClassType)fieldType).getParameters() :
                             PsiType.EMPTY_ARRAY;

      PsiType parameterType = getFirstElement(parameters);

      PsiType boundType = parameterType instanceof PsiWildcardType ?
                          ((PsiWildcardType)parameterType).getBound() :
                          null;

      return getPsiClass(boundType == null ? parameterType : boundType);
    }

    private static @Nullable PsiElement getTypeElement(@NotNull UField field) {
      UTypeReferenceExpression typeReference = field.getTypeReference();
      PsiElement typeReferencePsi = typeReference == null ?
                                    null :
                                    typeReference.getSourcePsi();

      return typeReferencePsi instanceof PsiTypeElement ?
             getFirstKey(((PsiTypeElement)typeReferencePsi)) :
             typeReferencePsi;
    }

    private static @Nullable PsiTypeElement getFirstKey(@NotNull PsiTypeElement typeElement) {
      PsiJavaCodeReferenceElement typeReference = typeElement.getInnermostComponentReferenceElement();
      if (typeReference == null) return null;

      PsiReferenceParameterList parameterList = typeReference.getParameterList();
      return parameterList == null ?
             null :
             getFirstElement(parameterList.getTypeParameterElements());
    }
  }

  private static final class ReplaceKeyQuickFix implements LocalQuickFix {

    private final @NotNull @NonNls String myText;

    private ReplaceKeyQuickFix(@NotNull String text) {
      myText = text;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return DevKitBundle.message("inspections.leakable.map.key.quick.fix.name", myText);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      assert isJavaTypeElement(element);

      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

      PsiTypeElement stringTypeElement = PsiElementFactory.getInstance(project)
        .createTypeElementFromText(myText, element.getContext());

      JavaCodeStyleManager.getInstance(project)
        .shortenClassReferences(element.replace(stringTypeElement));
    }

    static @NotNull LocalQuickFix @NotNull [] createFixesFor(@NotNull PsiElement element) {
      return isJavaTypeElement(element) ?
             new ReplaceKeyQuickFix[]{
               new ReplaceKeyQuickFix("? super " + JAVA_LANG_STRING_SHORT),
               new ReplaceKeyQuickFix(JAVA_LANG_STRING_SHORT)
             } :
             LocalQuickFix.EMPTY_ARRAY;
    }

    private static boolean isJavaTypeElement(@NotNull PsiElement element) {
      return element instanceof PsiTypeElement &&
             element.getLanguage().is(JavaLanguage.INSTANCE);
    }
  }
}
