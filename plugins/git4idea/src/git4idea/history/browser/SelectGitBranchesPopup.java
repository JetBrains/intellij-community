/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.util.List;

public class SelectGitBranchesPopup {
  private SelectGitBranchesPopup() {
  }

  public static void showMe(final Consumer<Object[]> selectedBranchConsumer,
                            final List<String> branchesAndTags,
                            boolean tags,
                            DataContext dc) {
    final JList list = new JBList();
    list.setCellRenderer(new BranchListCellRenderer(tags));
    list.setListData(ArrayUtil.toObjectArray(branchesAndTags));
    JBPopupFactory.getInstance().createListPopupBuilder(list)
            .setTitle("Select " + (tags ? "tags" : "branches"))
            .setResizable(true)
            .setDimensionServiceKey("Git.Select branches/tags")
            .setItemChoosenCallback(new Runnable() {
              public void run() {
                selectedBranchConsumer.consume(list.getSelectedValues());
              }
            })
            .createPopup().showInBestPositionFor(dc);
  }

  private static class BranchListCellRenderer extends ColoredListCellRenderer {
    private final boolean myTags;

    private BranchListCellRenderer(boolean tags) {
      myTags = tags;
    }

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof String) {
        append((String) value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }
}
