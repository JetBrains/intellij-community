package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.daemon.impl.HectorComponent;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.validation.AntHectorConfigurable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: May 12, 2008
 */
public class AntChangeContextFix extends BaseIntentionAction {
  public AntChangeContextFix() {
    setText(AntBundle.message("intention.configure.highlighting.text"));
  }

  @NotNull
  public final String getFamilyName() {
    return AntBundle.message("intention.configure.highlighting.family.name");
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final HectorComponent component = new HectorComponent(file);
    final JComponent focusComponent = findComponentToFocus(component);
    component.showComponent(JBPopupFactory.getInstance().guessBestPopupLocation(editor));
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        (focusComponent != null? focusComponent : component).requestFocus();
      }
    });
  }

  @Nullable
  private static JComponent findComponentToFocus(final JComponent component) {
    if (component.getClientProperty(AntHectorConfigurable.CONTEXTS_COMBO_KEY) != null) {
      return component;
    }
    for (Component child : component.getComponents()) {
      if (child instanceof JComponent) {
        final JComponent found = findComponentToFocus((JComponent)child);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }
}
