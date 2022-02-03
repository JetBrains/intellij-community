// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CachingCommittedChangesProvider;
import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Optional;

import static com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.VcsDataKeys.CHANGE_LISTS;
import static com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer.formatTextWithLinks;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.packNullables;
import static com.intellij.util.text.DateFormatUtil.formatPrettyDateTime;
import static com.intellij.util.ui.UIUtil.*;
import static java.lang.String.format;

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
    String htmlFormat = "<html><head>%s</head><body>%s</body></html>"; // NON-NLS
    String details = format(htmlFormat, getCssFontDeclaration(StartupUiUtil.getLabelFont()), getDetails(project, changeList));
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

  @Nls
  @NotNull
  private static String getDetails(@NotNull Project project, @NotNull CommittedChangeList changeList) {
    return join(packNullables(
      getNumber(changeList),
      getCommitterAndDate(changeList),
      getCustomDetails(changeList),
      formatTextWithLinks(project, changeList.getComment())
    ), BR);
  }

  @Nls
  @Nullable
  private static String getNumber(@NotNull CommittedChangeList changeList) {
    return Optional.ofNullable(changeList.getVcs())
      .map(AbstractVcs::getCachingCommittedChangesProvider)
      .map(CachingCommittedChangesProvider::getChangelistTitle)
      .map(changeListTitle -> changeListTitle + " #" + changeList.getNumber())
      .orElse(null);
  }

  @Nls
  @NotNull
  private static String getCommitterAndDate(@NotNull CommittedChangeList changeList) {
    @NonNls String committer = "<b>" + changeList.getCommitterName() + "</b>";
    return message("changelist.details.committed.format", committer, formatPrettyDateTime(changeList.getCommitDate()));
  }

  @Nls
  @Nullable
  private static String getCustomDetails(@NotNull CommittedChangeList changeList) {
    AbstractVcs vcs = changeList.getVcs();

    if (vcs != null && vcs.getCachingCommittedChangesProvider() != null) {
      CommittedChangeList originalChangeList = ReceivedChangeList.unwrap(changeList);

      List<ChangeListColumn> customColumns = ContainerUtil.filter(vcs.getCachingCommittedChangesProvider().getColumns(),
                                                                  ChangeListColumn::isCustom);
      if (customColumns.isEmpty()) return null;

      return new HtmlBuilder()
        .appendWithSeparators(HtmlChunk.br(), ContainerUtil.map(customColumns, column -> {
          return HtmlChunk.text(column.getTitle() + ": " + toString(column.getValue(originalChangeList)));
        }))
        .toString();
    }

    return null;
  }

  @Nls
  @NotNull
  private static String toString(@Nullable Object value) {
    String result = value != null ? value.toString() : ""; //NON-NLS
    return result.isEmpty() ? message("changes.none") : result;
  }
}
