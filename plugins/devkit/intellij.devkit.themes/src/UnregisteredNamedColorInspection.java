// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.idea.devkit.themes.metadata.ThemeMetadataJsonSchemaProviderFactoryKt;
import org.jetbrains.idea.devkit.themes.metadata.UIThemeMetadataService;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.Collection;
import java.util.List;

public class UnregisteredNamedColorInspection extends DevKitUastInspectionBase {

  private static final String JB_COLOR_FQN = JBColor.class.getCanonicalName();
  private static final String NAMED_COLOR_METHOD_NAME = "namedColor";

  @SuppressWarnings("unchecked")
  private static final Class<? extends UElement>[] U_ELEMENT_TYPES_HINT = new Class[]{UCallExpression.class};

  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitExpression(@NotNull UExpression node) {
        if (node instanceof UCallExpression) {
          handleCallExpression(holder, (UCallExpression)node);
        }
        else if (node instanceof UQualifiedReferenceExpression) {
          UElement parent = node.getUastParent();
          if (parent instanceof UCallExpression) {
            handleCallExpression(holder, (UCallExpression)parent);
          }
        }

        return true;
      }
    }, U_ELEMENT_TYPES_HINT);
  }

  private static void handleCallExpression(@NotNull ProblemsHolder holder, @NotNull UCallExpression expression) {
    if (!isNamedColorCall(expression)) return;

    String key = getKey(expression);
    if (key == null) return;

    if (!isRegisteredNamedColor(key)) {
      registerProblem(key, holder, expression);
    }
  }

  private static boolean isNamedColorCall(@NotNull UCallExpression expression) {
    if (expression.getKind() != UastCallKind.METHOD_CALL) return false;
    if (!NAMED_COLOR_METHOD_NAME.equals(expression.getMethodName())) return false;
    PsiMethod resolved = expression.resolve();
    if (resolved == null) return false;
    PsiClass containingClass = resolved.getContainingClass();
    return containingClass != null && JB_COLOR_FQN.equals(containingClass.getQualifiedName());
  }

  @Nullable
  private static String getKey(@NotNull UCallExpression expression) {
    List<UExpression> arguments = expression.getValueArguments();
    if (arguments.isEmpty()) return null;
    UExpression firstArgument = arguments.get(0);
    Object evaluated = firstArgument.evaluate();
    if (!(evaluated instanceof String)) return null;
    return (String)evaluated;
  }

  private static boolean isRegisteredNamedColor(@NotNull String key) {
    return UIThemeMetadataService.getInstance().findByKey(key) != null;
  }

  private static void registerProblem(@NotNull String key,
                                      @NotNull ProblemsHolder holder,
                                      @NotNull UCallExpression expression) {
    UIdentifier identifier = expression.getMethodIdentifier();
    if (identifier == null) return;
    PsiElement identifierPsi = identifier.getPsi();
    if (identifierPsi == null) return;

    holder.registerProblem(identifierPsi,
                           DevKitThemesBundle.message("inspections.unregistered.named.color", key), new LocalQuickFix() {

        @Nls(capitalization = Nls.Capitalization.Sentence)
        @NotNull
        @Override
        public String getFamilyName() {
          return DevKitThemesBundle.message("inspections.unregistered.named.color.fix.navigate.theme.metadata.file");
        }

        @Override
        public boolean startInWriteAction() {
          return false;
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
          final Collection<VirtualFile> metadataFiles =
            FilenameIndex.getAllFilesByExt(project, ThemeMetadataJsonSchemaProviderFactoryKt.THEME_METADATA_JSON_EXTENSION);
          if (metadataFiles.isEmpty()) return;

          final PsiFile[] psiFiles =
            PsiUtilCore.toPsiFiles(PsiManager.getInstance(project), metadataFiles).toArray(PsiFile.EMPTY_ARRAY);
          DataManager.getInstance().getDataContextFromFocusAsync()
            .onSuccess(context -> {
              NavigationUtil.getPsiElementPopup(psiFiles,
                                                DevKitThemesBundle.message(
                                                  "inspections.unregistered.named.color.fix.navigate.theme.metadata.file.popup.title"))
                .showInBestPositionFor(context);
            });
        }

        @Override
        public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
          return IntentionPreviewInfo.EMPTY;
        }
      });
  }
}
