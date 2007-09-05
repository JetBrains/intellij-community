package org.jetbrains.plugins.groovy.annotator.gutter;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.structure.GroovyElementPresentation;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * User: Dmitry.Krasilschikov
 * Date: 05.09.2007
 */
public class OverrideGutter extends GutterIconRenderer {
  private final AnAction myClickAction;

  private GrMethodsListCellRenderer GROOVY_METHOD_LIST_CELL_RNDERER = new GrMethodsListCellRenderer();
  private int myIconType;
  public static final int OVERRIDDEN_ICON_TYPE = 0;
  public static final int OVERRIDING_ICON_TYPE = 1;

  public OverrideGutter(final PsiMethod[] methods, int iconType) {
    myIconType = iconType;
    myClickAction = new AnAction() {

      public void actionPerformed(final AnActionEvent e) {
        if (methods.length == 0) {
        } else if (methods.length == 1) {
          // only one navigation target
          final PsiElement element = methods[0];
          if (element instanceof Navigatable && ((Navigatable) element).canNavigateToSource()) {
            ((Navigatable) element).navigate(true);
          }
        } else {
          // show popup for selecting navigation target from list
          final JBPopup gotoDeclarationPopup = NavigationUtil.getPsiElementPopup(
              methods,
              GROOVY_METHOD_LIST_CELL_RNDERER,
              GroovyBundle.message("goto.override.method.declaration"));

          gotoDeclarationPopup.show(new RelativePoint((MouseEvent) e.getInputEvent()));
        }
      }
    };
  }

  @Nullable
  public AnAction getClickAction() {
    return myClickAction;
  }

  @NotNull
  public Icon getIcon() {
    switch (myIconType) {
      case OVERRIDDEN_ICON_TYPE : return IconLoader.getIcon("/gutter/overridenMethod.png");
      case OVERRIDING_ICON_TYPE : return IconLoader.getIcon("/gutter/overridingMethod.png");
    }
    return IconLoader.getIcon("/gutter/overridenMethod.png");
  }

  class GrMethodsListCellRenderer extends PsiElementListCellRenderer {
    public String getElementText(PsiElement element) {
      assert element instanceof GrMethod;

      return GroovyElementPresentation.getPresentableText((GrMethod) element);
    }

    protected String getContainerText(PsiElement psiElement, String s) {
      return null;
    }

    protected int getIconFlags() {
      return Iconable.ICON_FLAG_CLOSED;
    }
  }
}
