// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.statistics.SpellcheckerActionStatistics;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.ui.AddDeleteListPanel;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SpellCheckerSettingsPane {

  static final class WordsPanel extends AddDeleteListPanel<String> {
    private final SpellCheckerManager manager;

    WordsPanel(SpellCheckerManager manager, @NotNull Disposable parentDisposable) {
      super(null, ContainerUtil.sorted(manager.getUserDictionaryWords()));
      this.manager = manager;
      getEmptyText().setText(SpellCheckerBundle.message("no.words"));

      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          myListModel.removeAllElements();
        }
      });
    }

    @Override
    protected void customizeDecorator(ToolbarDecorator decorator) {
      decorator.setRemoveAction((button) -> {
        SpellcheckerActionStatistics.removeWordFromAcceptedWords(manager.getProject());
        ListUtil.removeSelectedItems(myList);
      });
    }

    @Override
    protected String findItemToAdd() {
      SpellcheckerActionStatistics.addWordToAcceptedWords(manager.getProject());
      String word = Messages.showInputDialog(SpellCheckerBundle.message("enter.simple.word"),
                                             SpellCheckerBundle.message("add.new.word"), null);
      if (word == null) {
        return null;
      }
      else {
        word = word.trim();
      }

      if (!manager.hasProblem(word)) {
        Messages.showWarningDialog(SpellCheckerBundle.message("entered.word.0.is.correct.you.no.need.to.add.this.in.list", word),
                                   SpellCheckerBundle.message("add.new.word"));
        return null;
      }
      return word;
    }

    public @NotNull List<String> getWords() {
      Object[] pairs = getListItems();
      if (pairs == null) {
        return new ArrayList<>();
      }
      List<String> words = new ArrayList<>();
      for (Object pair : pairs) {
        words.add(pair.toString());
      }
      return words;
    }

    public boolean isModified() {
      List<String> newWords = getWords();
      Set<String> words = manager.getUserDictionaryWords();
      if (newWords.size() != words.size()) {
        return true;
      }
      Set<String> newHashWords = new HashSet<>(newWords);
      return !newHashWords.equals(words);
    }

    public void reset() {
      myListModel.removeAllElements();
      ContainerUtil.sorted(manager.getUserDictionaryWords()).forEach(myListModel::addElement);
    }
  }
}
