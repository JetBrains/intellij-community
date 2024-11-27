package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.codeInsight.navigation.UtilKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.util.PsiUtil;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public final class JourneyGoToActionUtil {
  private static final Logger LOG = Logger.getInstance(JourneyGoToActionUtil.class);

  public static void navigateWithPopup(
    Editor editor,
    @Nls @NotNull String title,
    List<PsiElement> result,
    Function<PsiElement,
    @NotNull TargetPresentation> presentationProvider,
    Consumer<PsiElement> onItemSelected
  ) {
    if (result.isEmpty()) {
      JBPopupFactory.getInstance()
          .createHtmlTextBalloonBuilder("Nothing found", MessageType.WARNING, null)
          .setFadeoutTime(3000)
          .createBalloon()
          .show(
            new RelativePoint(
              editor.getContentComponent(),
              editor.visualPositionToXY(editor.getCaretModel().getVisualPosition())
            ),
            Balloon.Position.above
          );

      return;
    }

    if (result.size() == 1) {
      onItemSelected.accept(result.get(0));
      return;
    }

    Project project = Objects.requireNonNull(editor.getProject());

    new PsiTargetNavigator<>(result)
      .presentationProvider(presentationProvider::apply)
      .createPopup(
        project,
        title,
        (e) -> {
          onItemSelected.accept(e);
          return true;
        }
      )
      .showInBestPositionFor(editor);
  }

  public static @NotNull TargetPresentation getPresentationOfClosestMember(PsiElement e1) {
    PsiElement element = e1.getNavigationElement();
    var member = PsiUtil.tryFindParentOrNull(element, it -> it instanceof PsiMember);
    if (member != null) {
      element = member;
    }
    return UtilKt.targetPresentation(element);
  }
}
