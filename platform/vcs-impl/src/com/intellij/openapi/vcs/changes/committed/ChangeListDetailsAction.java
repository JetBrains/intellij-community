/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.BrowserHyperlinkListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Optional;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.VcsDataKeys.CHANGE_LISTS;
import static com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer.formatTextWithLinks;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.packNullables;
import static com.intellij.util.text.DateFormatUtil.formatPrettyDateTime;
import static com.intellij.util.ui.UIUtil.*;
import static com.intellij.xml.util.XmlStringUtil.escapeString;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class ChangeListDetailsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(PROJECT);
    ChangeList[] changeLists = e.getRequiredData(CHANGE_LISTS);

    showDetailsPopup(project, (CommittedChangeList)changeLists[0]);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ChangeList[] changeLists = e.getData(CHANGE_LISTS);

    e.getPresentation().setEnabled(
      e.getProject() != null && changeLists != null && changeLists.length == 1 && changeLists[0] instanceof CommittedChangeList);
  }

  public static void showDetailsPopup(@NotNull Project project, @NotNull CommittedChangeList changeList) {
    String details =
      format("<html><head>%s</head><body>%s</body></html>", getCssFontDeclaration(getLabelFont()), getDetails(project, changeList));
    JEditorPane editorPane = new JEditorPane(HTML_MIME, details);
    editorPane.setEditable(false);
    editorPane.setBackground(HintUtil.getInformationColor());
    editorPane.select(0, 0);
    editorPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);

    JBPopupFactory.getInstance().createComponentPopupBuilder(createScrollPane(editorPane), editorPane)
      .setDimensionServiceKey(project, "changelist.details.popup", false)
      .setResizable(true)
      .setMovable(true)
      .setRequestFocus(true)
      .setTitle(message("changelist.details.title"))
      .createPopup()
      .showInBestPositionFor(DataManager.getInstance().getDataContext());
  }

  @NotNull
  private static String getDetails(@NotNull Project project, @NotNull CommittedChangeList changeList) {
    return join(packNullables(
      getNumber(changeList),
      getCommitterAndDate(changeList),
      getCustomDetails(changeList),
      formatTextWithLinks(project, changeList.getComment())
    ), "<br>");
  }

  @Nullable
  private static String getNumber(@NotNull CommittedChangeList changeList) {
    return Optional.ofNullable(changeList.getVcs())
      .map(AbstractVcs::getCachingCommittedChangesProvider)
      .map(CachingCommittedChangesProvider::getChangelistTitle)
      .map(changeListTitle -> changeListTitle + " #" + changeList.getNumber())
      .orElse(null);
  }

  @NotNull
  private static String getCommitterAndDate(@NotNull CommittedChangeList changeList) {
    @NonNls String committer = "<b>" + changeList.getCommitterName() + "</b>";
    return message("changelist.details.committed.format", committer, formatPrettyDateTime(changeList.getCommitDate()));
  }

  @Nullable
  private static String getCustomDetails(@NotNull CommittedChangeList changeList) {
    AbstractVcs vcs = changeList.getVcs();

    if (vcs != null && vcs.getCachingCommittedChangesProvider() != null) {
      CommittedChangeList originalChangeList = ReceivedChangeList.unwrap(changeList);

      return nullize(
        stream(vcs.getCachingCommittedChangesProvider().getColumns())
          .filter(ChangeListColumn::isCustom)
          .map(column -> column.getTitle() + ": " + escapeString(toString(column.getValue(originalChangeList))))
          .collect(joining("<br>"))
      );
    }

    return null;
  }

  @NotNull
  private static String toString(@Nullable Object value) {
    String result = value != null ? value.toString() : "";
    return result.isEmpty() ? "<none>" : result;
  }
}
