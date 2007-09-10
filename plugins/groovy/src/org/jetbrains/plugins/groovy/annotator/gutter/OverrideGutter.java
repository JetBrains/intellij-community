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
import com.intellij.psi.PsiClass;
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

  private GrMethodsListCellRenderer GROOVY_METHOD_LIST_CELL_RENDERER = new GrMethodsListCellRenderer();
  private boolean myIsImplements;

  private String myTooltipText;

  @Nullable
  public String getTooltipText() {
    return myTooltipText;
  }

  public static final int OVERRIDING_ICON_TYPE = 1;

  public OverrideGutter(final PsiMethod[] methods, boolean isImplements) {
    myIsImplements = isImplements;
    myTooltipText = getTooltipText(methods);
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
              GROOVY_METHOD_LIST_CELL_RENDERER,
              GroovyBundle.message("goto.override.method.declaration"));

          gotoDeclarationPopup.show(new RelativePoint((MouseEvent) e.getInputEvent()));
        }
      }
    };
  }

  private String getTooltipText(PsiMethod[] methods) {
    assert methods.length > 0;
    final PsiClass containingClass = methods[0].getContainingClass();
    assert containingClass != null; //otherwise it could not have been overridden
    String classDescr = containingClass.getQualifiedName();
    if (classDescr == null) classDescr = containingClass.getName();
    if (myIsImplements) {
      return GroovyBundle.message("implements.method.from.super", classDescr);
    }
    return GroovyBundle.message("overrides.method.from.super", classDescr);
  }

  @Nullable
  public AnAction getClickAction() {
    return myClickAction;
  }

  @NotNull
  public Icon getIcon() {
    if (myIsImplements) {
      return IconLoader.getIcon("/gutter/implementingMethod.png");
    }
    return IconLoader.getIcon("/gutter/overridingMethod.png");
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
