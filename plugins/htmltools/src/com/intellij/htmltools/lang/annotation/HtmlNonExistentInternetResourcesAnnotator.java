package com.intellij.htmltools.lang.annotation;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.DisableInspectionToolAction;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.htmltools.codeInspection.htmlInspections.HtmlNonExistentInternetResourceInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.paths.WebReferencesAnnotatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class HtmlNonExistentInternetResourcesAnnotator extends WebReferencesAnnotatorBase {
  public static boolean ourEnableInTestMode = false;

  @Override
  protected WebReference @NotNull [] collectWebReferences(@NotNull PsiFile file) {
    if (!ourEnableInTestMode && ApplicationManager.getApplication().isUnitTestMode()) {
      return EMPTY_ARRAY;
    }

    final HtmlNonExistentInternetResourceInspection inspection = getInspection(file);
    if (inspection == null) {
      return EMPTY_ARRAY;
    }

    final List<WebReference> result = new ArrayList<>();

    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);

        if (!(element instanceof XmlAttributeValue)) {
          return;
        }

        final WebReference webReference = lookForWebReference(element);
        if (webReference != null) {
          result.add(webReference);
        }
      }
    });
    return result.toArray(new WebReference[0]);
  }

  public static @Nullable HtmlNonExistentInternetResourceInspection getInspection(@NotNull PsiElement context) {
    final String shortName = HtmlNonExistentInternetResourceInspection.SHORT_NAME;

    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null) {
      return null;
    }

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getCurrentProfile();
    if (!profile.isToolEnabled(key, context)) {
      return null;
    }
    return (HtmlNonExistentInternetResourceInspection)profile.getUnwrappedTool(shortName, context);
  }

  @Override
  protected IntentionAction @NotNull [] getQuickFixes() {
    return new IntentionAction[] {new MyDisableInspectionFix()};
  }

  @Override
  protected @NotNull HighlightDisplayLevel getHighlightDisplayLevel(@NotNull PsiElement context) {
    final InspectionProfile inspectionProfile =
      InspectionProjectProfileManager.getInstance(context.getProject()).getCurrentProfile();
    final HighlightDisplayKey displayKey = HighlightDisplayKey.find(HtmlNonExistentInternetResourceInspection.SHORT_NAME);
    return inspectionProfile.getErrorLevel(displayKey, context);
  }

  @Override
  protected @NotNull String getErrorMessage(@NotNull String url) {
    return HtmlToolsBundle.message("html.inspections.non.existent.internet.resource.message", url);
  }

  private static final class MyDisableInspectionFix implements IntentionAction {
    private final DisableInspectionToolAction myDisableInspectionToolAction;

    private MyDisableInspectionFix() {
      final HighlightDisplayKey key = HighlightDisplayKey.find(HtmlNonExistentInternetResourceInspection.SHORT_NAME);
      assert key != null;
      myDisableInspectionToolAction = new DisableInspectionToolAction(key);
    }

    @Override
    public @NotNull String getText() {
      return HtmlToolsBundle.message("html.intention.disable.validation.web.links");
    }

    @Override
    public @NotNull String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
      myDisableInspectionToolAction.invoke(project, editor, psiFile);
    }

    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
      return myDisableInspectionToolAction.getElementToMakeWritable(file);
    }

    @Override
    public boolean startInWriteAction() {
      return myDisableInspectionToolAction.startInWriteAction();
    }
  }
}
