// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.FontUtil.spaceAndThinSpace;

class ShelvedListNode extends ChangesBrowserNode<ShelvedChangeList> {
  private final @NotNull ShelvedChangeList myList;

  ShelvedListNode(@NotNull ShelvedChangeList list) {
    super(list);
    myList = list;
  }

  public @NotNull ShelvedChangeList getList() {
    return myList;
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    String listName = myList.getDescription();
    if (StringUtil.isEmptyOrSpaces(listName)) listName = VcsBundle.message("changes.nodetitle.empty.changelist.name");

    if (myList.isRecycled() || myList.isDeleted()) {
      renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }
    else {
      renderer.appendTextWithIssueLinks(listName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    appendCount(renderer);
    String date = DateFormatUtil.formatPrettyDateTime(myList.getDate());
    renderer.append(", " + date, SimpleTextAttributes.GRAYED_ATTRIBUTES);

    String loadingError = myList.getChangesLoadingError();
    if (loadingError != null) {
      renderer.append(spaceAndThinSpace() + loadingError, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
  }

  @Override
  public @Nls String getTextPresentation() {
    return getUserObject().toString();
  }
}
