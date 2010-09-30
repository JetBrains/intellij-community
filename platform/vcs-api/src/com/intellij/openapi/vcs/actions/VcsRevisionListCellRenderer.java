/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.text.DateFormatUtil;

import javax.swing.*;

public class VcsRevisionListCellRenderer extends ColoredListCellRenderer {
  protected void customizeCellRenderer(JList list,
                                       Object value,
                                       int index,
                                       boolean selected,
                                       boolean hasFocus) {

    append(getRevisionString(((VcsFileRevision)value)), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
  }

  private String getRevisionString(final VcsFileRevision revision) {
    final StringBuffer result = new StringBuffer();
    result.append(revision.getRevisionNumber().asString());
    final String branchName = revision.getBranchName();
    if (branchName != null && branchName.length() > 0) {
      result.append("(");
      result.append(branchName);
      result.append(")");
    }
    result.append(" ");
    result.append(DateFormatUtil.formatPrettyDateTime(revision.getRevisionDate()));
    result.append(" ");
    result.append(revision.getAuthor());
    return  result.toString();
  }
}
