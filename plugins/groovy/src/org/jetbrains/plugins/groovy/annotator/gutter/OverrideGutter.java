/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
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
          final Navigatable method = methods[0];
          if (method.canNavigateToSource()) {
            method.navigate(true);
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
//      assert element instanceof GrMethod;

      return GroovyElementPresentation.getPresentableText((GroovyPsiElement) element);
    }

    protected String getContainerText(PsiElement psiElement, String s) {
      return null;
    }

    protected int getIconFlags() {
      return Iconable.ICON_FLAG_CLOSED;
    }
  }
}
