// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.openapi.vcs.changes.ChangeListDataKt.getChangeListData;
import static com.intellij.util.FontUtil.spaceAndThinSpace;
import static one.util.streamex.StreamEx.of;


public class ChangesBrowserChangeListNode extends ChangesBrowserNode<ChangeList> {
  private final Project myProject;
  private final ChangeListManagerEx myClManager;
  private final ChangeListRemoteState myChangeListRemoteState;

  public ChangesBrowserChangeListNode(Project project, ChangeList userObject, final ChangeListRemoteState changeListRemoteState) {
    super(userObject);
    myProject = project;
    myChangeListRemoteState = changeListRemoteState;
    myClManager = ChangeListManagerEx.getInstanceEx(project);
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    if (userObject instanceof LocalChangeList list) {
      String listName = list.getName();
      if (StringUtil.isEmptyOrSpaces(listName)) listName = VcsBundle.message("changes.nodetitle.empty.changelist.name");
      renderer.appendTextWithIssueLinks(listName, list.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                                                   : SimpleTextAttributes.REGULAR_ATTRIBUTES);
      if (getChangeListData(list) != null) {
        renderer.append(" (i)", SimpleTextAttributes.GRAYED_ATTRIBUTES); //NON-NLS
        renderer.setToolTipText(getTooltipText());
      }
      appendCount(renderer);
      for (ChangeListDecorator decorator : ChangeListDecorator.getDecorators(myProject)) {
        decorator.decorateChangeList(list, renderer, selected, expanded, hasFocus);
      }
      final String freezed = myClManager.isFreezed();
      if (freezed != null) {
        renderer.append(spaceAndThinSpace() + freezed, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      else if (myClManager.isInUpdate()) {
        appendUpdatingState(renderer);
      }
      if (!myChangeListRemoteState.allUpToDate()) {
        renderer.append(spaceAndThinSpace());
        renderer.append(VcsBundle.message("changes.nodetitle.have.outdated.files"), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
    else {
      renderer.append(getUserObject().getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      appendCount(renderer);
    }
  }

  @NlsContexts.Tooltip
  @Nullable
  private String getTooltipText() {
    if (!(userObject instanceof LocalChangeList)) return null;
    ChangeListData data = getChangeListData((LocalChangeList)userObject);
    if (data == null) return null;

    String dataInfo = data.getPresentation();
    String message = cropMessageIfNeeded(((LocalChangeList)userObject).getComment());

    @Nls StringBuilder sb = new StringBuilder();
    if (!StringUtil.isEmpty(dataInfo)) sb.append(dataInfo);
    if (!StringUtil.isEmpty(message)) {
      if (sb.length() > 0) sb.append(UIUtil.BR).append(UIUtil.BR);
      sb.append(message);
    }
    return nullize(sb.toString());
  }

  /**
   * Get first 5 lines from the comment and add ellipsis if are smth else
   */
  @Nullable
  private static String cropMessageIfNeeded(@Nullable String comment) {
    if (comment == null) return null;
    String[] lines = StringUtil.splitByLines(XmlStringUtil.escapeString(comment), false);
    String croppedMessage = of(lines).limit(5).joining(UIUtil.BR);
    return lines.length > 5 ? croppedMessage + "..." : croppedMessage;
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName().trim();
  }

  @ApiStatus.Internal
  @Override
  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    final List<Change> changes = dragBean.getChanges();
    for (Change change : getUserObject().getChanges()) {
      for (Change incomingChange : changes) {
        if (change == incomingChange) return false;
      }
    }

    return true;
  }

  @ApiStatus.Internal
  @Override
  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
    if (!(userObject instanceof LocalChangeList)) {
      return;
    }
    final LocalChangeList dropList = (LocalChangeList)getUserObject();
    dragOwner.moveChangesTo(dropList, dragBean.getChanges());

    final List<FilePath> toUpdate = new ArrayList<>();

    addIfNotNull(toUpdate, dragBean.getUnversionedFiles());
    addIfNotNull(toUpdate, dragBean.getIgnoredFiles());
    if (!toUpdate.isEmpty()) {
      dragOwner.addUnversionedFiles(dropList, ContainerUtil.mapNotNull(toUpdate, FilePath::getVirtualFile));
    }
  }

  private static void addIfNotNull(final List<? super FilePath> unversionedFiles, final List<? extends FilePath> ignoredFiles) {
    if (ignoredFiles != null) {
      unversionedFiles.addAll(ignoredFiles);
    }
  }

  @Override
  public int getSortWeight() {
    if (userObject instanceof LocalChangeList && ((LocalChangeList)userObject).isDefault()) return DEFAULT_CHANGE_LIST_SORT_WEIGHT;
    return CHANGE_LIST_SORT_WEIGHT;
  }

  @Override
  public int compareUserObjects(final ChangeList o2) {
    return compareFileNames(getUserObject().getName(), o2.getName());
  }
}
