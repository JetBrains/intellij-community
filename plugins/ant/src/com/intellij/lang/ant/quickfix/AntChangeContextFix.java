// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.daemon.impl.HectorComponent;
import com.intellij.codeInsight.daemon.impl.HectorComponentFactory;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class AntChangeContextFix extends BaseIntentionAction {
  public AntChangeContextFix() {
    setText(AntBundle.message("intention.configure.highlighting.text"));
  }

  @Override
  @NotNull
  public final String getFamilyName() {
    return AntBundle.message("intention.configure.highlighting.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    //if (!(file instanceof XmlFile)) {
    //  return false;
    //}
    //final XmlTag xmlTag = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), XmlTag.class);
    //if (xmlTag == null) {
    //  return false;
    //}
    //final AntDomElement antDomElement = AntSupport.getAntDomElement(xmlTag);
    //if (antDomElement == null) {
    //  return false;
    //}
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final HectorComponent component = project.getService(HectorComponentFactory.class).create(file);
    //final JComponent focusComponent = findComponentToFocus(component);
    component.showComponent(JBPopupFactory.getInstance().guessBestPopupLocation(editor));
    //SwingUtilities.invokeLater(new Runnable() {
    //  public void run() {
    //    (focusComponent != null? focusComponent : component).requestFocus();
    //  }
    //});
  }

  //@Nullable
  //private static JComponent findComponentToFocus(final JComponent component) {
  //  if (component.getClientProperty(AntHectorConfigurable.CONTEXTS_COMBO_KEY) != null) {
  //    return component;
  //  }
  //  for (Component child : component.getComponents()) {
  //    if (child instanceof JComponent) {
  //      final JComponent found = findComponentToFocus((JComponent)child);
  //      if (found != null) {
  //        return found;
  //      }
  //    }
  //  }
  //  return null;
  //}
}
