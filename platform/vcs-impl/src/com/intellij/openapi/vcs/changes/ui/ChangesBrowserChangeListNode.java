/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.util.FontUtil.spaceAndThinSpace;
import static one.util.streamex.StreamEx.of;

/**
 * @author yole
 */
public class ChangesBrowserChangeListNode extends ChangesBrowserNode<ChangeList> {
  private final List<ChangeListDecorator> myDecorators;
  private final ChangeListManagerEx myClManager;
  private final ChangeListRemoteState myChangeListRemoteState;

  public ChangesBrowserChangeListNode(Project project, ChangeList userObject, final ChangeListRemoteState changeListRemoteState) {
    super(userObject);
    myChangeListRemoteState = changeListRemoteState;
    myClManager = (ChangeListManagerEx) ChangeListManager.getInstance(project);
    myDecorators = ServiceKt.getComponents(project, ChangeListDecorator.class);
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    if (userObject instanceof LocalChangeList) {
      final LocalChangeList list = ((LocalChangeList)userObject);
      renderer.appendTextWithIssueLinks(list.getName(),
             list.isDefault() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
      if (list.getData() != null) {
        renderer.append(" \u24D8", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        renderer.setToolTipText(getTooltipText());
      }
      appendCount(renderer);
      for (ChangeListDecorator decorator: myDecorators) {
        decorator.decorateChangeList(list, renderer, selected, expanded, hasFocus);
      }
      final String freezed = myClManager.isFreezed();
      if (freezed != null) {
        renderer.append(spaceAndThinSpace() + freezed, SimpleTextAttributes.GRAYED_ATTRIBUTES);
      } 
      else if (myClManager.isInUpdate()) {
        appendUpdatingState(renderer);
      }
      if (! myChangeListRemoteState.allUpToDate()) {
        renderer.append(spaceAndThinSpace());
        renderer.append(VcsBundle.message("changes.nodetitle.have.outdated.files"), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
    else {
      renderer.append(getUserObject().getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
      appendCount(renderer);
    }
  }

  @Nullable
  private String getTooltipText() {
    if (!(userObject instanceof LocalChangeList)) return null;
    Object data = ((LocalChangeList)userObject).getData();
    if (!(data instanceof ChangeListData)) return null;
    String dataInfo = XmlStringUtil.escapeString(((ChangeListData)data).getPresentation());
    String message = cropMessageIfNeeded(((LocalChangeList)userObject).getComment());
    return nullize(of(dataInfo, message).nonNull().joining("\n"));
  }

  /**
   * Get first 5 lines from the comment and add ellipsis if are smth else
   */
  @Nullable
  private static String cropMessageIfNeeded(@Nullable String comment) {
    if (comment == null) return null;
    String[] lines = StringUtil.splitByLines(comment, false);
    String croppedMessage = of(lines).limit(5).joining("\n");
    return lines.length > 5 ? croppedMessage + "..." : croppedMessage;
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName().trim();
  }

  @Override
  public boolean canAcceptDrop(final ChangeListDragBean dragBean) {
    final Change[] changes = dragBean.getChanges();
    for (Change change : getUserObject().getChanges()) {
      for (Change incomingChange : changes) {
        if (change == incomingChange) return false;
      }
    }

    return true;
  }

  @Override
  public void acceptDrop(final ChangeListOwner dragOwner, final ChangeListDragBean dragBean) {
    if (!(userObject instanceof LocalChangeList)) {
      return;
    }
    final LocalChangeList dropList = (LocalChangeList)getUserObject();
    dragOwner.moveChangesTo(dropList, dragBean.getChanges());

    final List<VirtualFile> toUpdate = new ArrayList<>();

    addIfNotNull(toUpdate, dragBean.getUnversionedFiles());
    addIfNotNull(toUpdate, dragBean.getIgnoredFiles());
    if (! toUpdate.isEmpty()) {
      dragOwner.addUnversionedFiles(dropList, toUpdate);
    }
  }

  private static void addIfNotNull(final List<VirtualFile> unversionedFiles1, final List<VirtualFile> ignoredFiles) {
    if (ignoredFiles != null) {
      unversionedFiles1.addAll(ignoredFiles);
    }
  }

  @Override
  public int getSortWeight() {
    if (userObject instanceof LocalChangeList && ((LocalChangeList)userObject).isDefault()) return DEFAULT_CHANGE_LIST_SORT_WEIGHT;
    return CHANGE_LIST_SORT_WEIGHT;
  }

  @Override
  public int compareUserObjects(final ChangeList o2) {
    return getUserObject().getName().compareToIgnoreCase(o2.getName());
  }
}
