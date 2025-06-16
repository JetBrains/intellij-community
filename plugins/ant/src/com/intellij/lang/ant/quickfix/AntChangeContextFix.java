// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  public final @NotNull String getFamilyName() {
    return AntBundle.message("intention.configure.highlighting.family.name");
  }

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile psiFile) {
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
  public void invoke(final @NotNull Project project, final Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
    final HectorComponent component = project.getService(HectorComponentFactory.class).create(psiFile);
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
