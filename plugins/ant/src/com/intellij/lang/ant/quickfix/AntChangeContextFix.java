/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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
package com.intellij.lang.ant.quickfix;

import com.intellij.codeInsight.daemon.impl.HectorComponent;
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

  @NotNull
  public final String getFamilyName() {
    return AntBundle.message("intention.configure.highlighting.family.name");
  }

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

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final HectorComponent component = new HectorComponent(file);
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
