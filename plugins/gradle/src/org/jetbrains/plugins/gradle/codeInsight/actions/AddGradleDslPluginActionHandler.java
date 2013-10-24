/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.KeyValue;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import javax.swing.*;
import java.awt.*;

/**
* @author Vladislav.Soroka
* @since 10/24/13
*/
class AddGradleDslPluginActionHandler implements CodeInsightActionHandler {
  private final KeyValue[] myPlugins;
  public AddGradleDslPluginActionHandler(KeyValue[] plugins) {
    myPlugins = plugins;
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;

    final JBList list = new JBList(myPlugins);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new MyListCellRenderer());
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        final KeyValue selected = (KeyValue)list.getSelectedValue();
        new WriteCommandAction.Simple(project, GradleBundle.message("gradle.codeInsight.action.apply_plugin.text"), file) {
          @Override
          protected void run() {
            if (selected == null) return;
            GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
            GrStatement grStatement = factory.createStatementFromText(
              String.format("apply plugin: '%s'", selected.getKey()), null);

            PsiElement anchor = file.findElementAt(editor.getCaretModel().getOffset());
            PsiElement currentElement = PsiTreeUtil.getParentOfType(anchor, GrClosableBlock.class, GroovyFile.class);
            if (currentElement != null) {
              currentElement.addAfter(grStatement, anchor);
            }
            else {
              file.addAfter(grStatement, file.findElementAt(editor.getCaretModel().getOffset() - 1));
            }
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
            Document document = documentManager.getDocument(file);
            if (document != null) {
              documentManager.commitDocument(document);
            }
          }
        }.execute();
      }
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      KeyValue descriptor = ContainerUtil.find(myPlugins, new Condition<KeyValue>() {
        @Override
        public boolean value(KeyValue value) {
          return value.getKey().equals(AddGradleDslPluginAction.TEST_THREAD_LOCAL.get());
        }
      });
      list.setSelectedValue(descriptor, false);
      runnable.run();
    }
    else {
      JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle(GradleBundle.message("gradle.codeInsight.action.apply_plugin.popup.title"))
        .setItemChoosenCallback(runnable)
        .setFilteringEnabled(new Function<Object, String>() {
          @Override
          public String fun(Object o) {
            return String.valueOf(((KeyValue)o).getKey());
          }
        })
        .createPopup()
        .showInBestPositionFor(editor);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static class MyListCellRenderer implements ListCellRenderer {
    private final JPanel myPanel;
    private final JLabel myNameLabel;
    private final JLabel myDescLabel;

    public MyListCellRenderer() {
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
      myNameLabel = new JLabel();

      myPanel.add(myNameLabel, BorderLayout.WEST);
      myPanel.add(new JLabel("     "));
      myDescLabel = new JLabel();
      myPanel.add(myDescLabel, BorderLayout.EAST);

      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      Font font = scheme.getFont(EditorFontType.PLAIN);
      myNameLabel.setFont(font);
      myDescLabel.setFont(font);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

      KeyValue descriptor = (KeyValue)value;
      Color backgroundColor = isSelected ? list.getSelectionBackground() : list.getBackground();

      myNameLabel.setText(String.valueOf(descriptor.getKey()));
      myNameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      myPanel.setBackground(backgroundColor);

      String description = String.format("<html><div WIDTH=%d>%s</div><html>", 400, String.valueOf(descriptor.getValue()));
      myDescLabel.setText(description);
      myDescLabel.setForeground(LookupCellRenderer.getGrayedForeground(isSelected));
      myDescLabel.setBackground(backgroundColor);

      return myPanel;
    }
  }
}
