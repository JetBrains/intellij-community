/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.SimpleTextAttributes;
import org.intellij.plugins.intelliLang.inject.InjectedLanguage;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LanguagePanel extends AbstractInjectionPanel<BaseInjection> {
  private JPanel myRoot;
  private ComboBox myLanguage;
  private EditorTextField myPrefix;
  private EditorTextField mySuffix;

  public LanguagePanel(Project project, BaseInjection injection) {
    super(injection, project);
    $$$setupUI$$$();

    final String[] languageIDs = InjectedLanguage.getAvailableLanguageIDs();
    Arrays.sort(languageIDs);

    myLanguage.setModel(new DefaultComboBoxModel(languageIDs));
    myLanguage.setRenderer(new ColoredListCellRendererWrapper<String>() {
      final Set<String> IDs = new HashSet<>(Arrays.asList(languageIDs));

      @Override
      protected void doCustomize(JList list, String s, int index, boolean selected, boolean hasFocus) {
        final SimpleTextAttributes attributes =
            IDs.contains(s) ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.ERROR_ATTRIBUTES;
        append(s, attributes);

        final Language language = InjectedLanguage.findLanguageById(s);
        if (language != null) {
          final FileType fileType = language.getAssociatedFileType();
          if (fileType != null) {
            setIcon(fileType.getIcon());
            append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append("(" + fileType.getDescription() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
        }
      }
    });
    myLanguage.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          updateHighlighters();
        }
      }
    });

    myRoot.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        updateHighlighters();
      }
    });
  }

  private void updateHighlighters() {
    final EditorImpl editor = ((EditorImpl)myPrefix.getEditor());
    if (editor == null) return;

    final EditorImpl editor2 = ((EditorImpl)mySuffix.getEditor());
    assert editor2 != null;

    final Language language = InjectedLanguage.findLanguageById(getLanguage());
    if (language == null) {
      editor.setHighlighter(new LexerEditorHighlighter(new PlainSyntaxHighlighter(), editor.getColorsScheme()));
      editor2.setHighlighter(new LexerEditorHighlighter(new PlainSyntaxHighlighter(), editor.getColorsScheme()));
    }
    else {
      final SyntaxHighlighter s1 = SyntaxHighlighterFactory.getSyntaxHighlighter(language, myProject, null);
      final SyntaxHighlighter s2 = SyntaxHighlighterFactory.getSyntaxHighlighter(language, myProject, null);
      editor.setHighlighter(new LexerEditorHighlighter(s1, editor.getColorsScheme()));
      editor2.setHighlighter(new LexerEditorHighlighter(s2, editor2.getColorsScheme()));
    }
  }

  @NotNull
  public String getLanguage() {
    return (String)myLanguage.getSelectedItem();
  }

  public void setLanguage(String id) {
    final DefaultComboBoxModel model = (DefaultComboBoxModel)myLanguage.getModel();
    if (model.getIndexOf(id) == -1 && id.length() > 0) {
      model.insertElementAt(id, 0);
    }
    myLanguage.setSelectedItem(id);
    updateHighlighters();
  }

  public String getPrefix() {
    return myPrefix.getText();
  }

  public void setPrefix(String s) {
    if (!myPrefix.getText().equals(s)) {
      myPrefix.setText(s);
    }
  }

  public String getSuffix() {
    return mySuffix.getText();
  }

  public void setSuffix(String s) {
    if (!mySuffix.getText().equals(s)) {
      mySuffix.setText(s);
    }
  }

  @Override
  protected void resetImpl() {
    setLanguage(myOrigInjection.getInjectedLanguageId());
    setPrefix(myOrigInjection.getPrefix());
    setSuffix(myOrigInjection.getSuffix());
  }

  @Override
  protected void apply(BaseInjection i) {
    i.setInjectedLanguageId(getLanguage());
    i.setPrefix(getPrefix());
    i.setSuffix(getSuffix());
  }

  @Override
  public JPanel getComponent() {
    return myRoot;
  }

  private void $$$setupUI$$$() {
  }
}
