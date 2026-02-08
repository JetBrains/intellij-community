// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListData;
import com.intellij.openapi.vcs.changes.ChangeListDecorator;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.platform.vcs.changes.ChangeListManagerState;
import com.intellij.platform.vcs.impl.shared.changes.ChangeListDnDSupport;
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.openapi.vcs.changes.ChangeListDataKt.getChangeListData;
import static com.intellij.util.FontUtil.spaceAndThinSpace;

public class ChangesBrowserChangeListNode extends ChangesBrowserNode<ChangeList> {
  private final @NotNull Project myProject;
  private final @NotNull ChangeListRemoteState myChangeListRemoteState;

  public ChangesBrowserChangeListNode(@NotNull Project project,
                                      @NotNull ChangeList userObject,
                                      @NotNull final ChangeListRemoteState changeListRemoteState) {
    super(userObject);
    myProject = project;
    myChangeListRemoteState = changeListRemoteState;
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
      ChangeListManagerState clManagerState = ChangeListsViewModel.getInstance(myProject).getChangeListManagerState().getValue();
      if (clManagerState instanceof ChangeListManagerState.Frozen frozen) {
        renderer.append(spaceAndThinSpace() + frozen.getReason(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      } else if (clManagerState instanceof ChangeListManagerState.Updating) {
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

  private @NlsContexts.Tooltip @Nullable String getTooltipText() {
    if (!(userObject instanceof LocalChangeList)) return null;
    ChangeListData data = getChangeListData((LocalChangeList)userObject);
    if (data == null) return null;

    String dataInfo = data.getPresentation();
    String message = cropMessageIfNeeded(((LocalChangeList)userObject).getComment());

    @Nls StringBuilder sb = new StringBuilder();
    if (!StringUtil.isEmpty(dataInfo)) sb.append(dataInfo);
    if (!StringUtil.isEmpty(message)) {
      if (!sb.isEmpty()) sb.append(UIUtil.BR).append(UIUtil.BR);
      sb.append(message);
    }
    return nullize(sb.toString());
  }

  /**
   * Get first 5 lines from the comment and add ellipsis if are smth else
   */
  private static @Nullable String cropMessageIfNeeded(@Nullable String comment) {
    if (comment == null) return null;
    String[] lines = StringUtil.splitByLines(XmlStringUtil.escapeString(comment), false);
    String croppedMessage = Arrays.stream(lines).limit(5).collect(Collectors.joining(UIUtil.BR));
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
  public void acceptDrop(final ChangeListDnDSupport dragOwner, final ChangeListDragBean dragBean) {
    if (!(userObject instanceof LocalChangeList)) {
      return;
    }
    final LocalChangeList dropList = (LocalChangeList)getUserObject();
    dragOwner.moveChangesTo(dropList, dragBean.getChanges());

    final List<FilePath> toUpdate = new ArrayList<>();

    addIfNotNull(toUpdate, dragBean.getUnversionedFiles());
    addIfNotNull(toUpdate, dragBean.getIgnoredFiles());
    if (!toUpdate.isEmpty()) {
      dragOwner.addUnversionedFiles(dropList, toUpdate);
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

  public @NotNull ChangeListRemoteState getChangeListRemoteState() {
    return myChangeListRemoteState;
  }
}
