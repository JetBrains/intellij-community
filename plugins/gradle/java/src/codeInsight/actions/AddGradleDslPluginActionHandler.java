// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
* @author Vladislav.Soroka
*/
class AddGradleDslPluginActionHandler implements CodeInsightActionHandler {
  private final List<? extends Pair<String, String>> myPlugins;

  AddGradleDslPluginActionHandler(List<? extends Pair<String, String>> plugins) {
    myPlugins = plugins;
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(file)) return;

    final JBList list = new JBList(myPlugins);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new MyListCellRenderer());
    Runnable runnable = () -> {
      final Pair selected = (Pair)list.getSelectedValue();
      WriteCommandAction.writeCommandAction(project, file).withName(GradleBundle.message("gradle.codeInsight.action.apply_plugin.text"))
                        .run(() -> {
                          if (selected == null) return;
                          GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
                          GrStatement grStatement =
                            factory.createStatementFromText(String.format("apply plugin: '%s'", selected.first), null);

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
                        });
    };

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Pair<String, String> descriptor = ContainerUtil.find(myPlugins, value -> value.first.equals(AddGradleDslPluginAction.TEST_THREAD_LOCAL.get()));
      list.setSelectedValue(descriptor, false);
      runnable.run();
    }
    else {
      JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle(GradleBundle.message("gradle.codeInsight.action.apply_plugin.popup.title"))
        .setItemChoosenCallback(runnable)
        .setNamerForFiltering(o -> String.valueOf(((Pair)o).first))
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

    MyListCellRenderer() {
      myPanel = new JPanel(new BorderLayout());
      myPanel.setBorder(JBUI.Borders.emptyLeft(2));
      myNameLabel = new JLabel();

      myPanel.add(myNameLabel, BorderLayout.WEST);
      myPanel.add(new JLabel("     "));
      myDescLabel = new JLabel();
      myPanel.add(myDescLabel, BorderLayout.EAST);

      Font font = EditorFontType.getGlobalPlainFont();
      myNameLabel.setFont(font);
      myDescLabel.setFont(font);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Pair descriptor = (Pair)value;
      Color backgroundColor = isSelected ? list.getSelectionBackground() : list.getBackground();

      myNameLabel.setText(String.valueOf(descriptor.first));
      myNameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      myPanel.setBackground(backgroundColor);

      String description =
        new HtmlBuilder()
          .append(String.valueOf(descriptor.second))
          .wrapWith(HtmlChunk.div().attr("WIDTH", "400"))
          .wrapWith("html")
          .toString();
      myDescLabel.setText(description);
      myDescLabel.setForeground(LookupCellRenderer.getGrayedForeground(isSelected));
      myDescLabel.setBackground(backgroundColor);

      return myPanel;
    }
  }
}