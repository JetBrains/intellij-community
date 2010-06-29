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
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.util.List;

public class UsersPopup {
  private UsersPopup() {
  }

  public static void showUsersPopup(final List<String> knownUsers, final Consumer<String> continuation, DataContext dc) {
    // todo pattern to come
    final JList list = new JBList();
    list.setListData(ArrayUtil.toObjectArray(knownUsers));
    new ListSpeedSearch(list);

    JBPopupFactory.getInstance().createListPopupBuilder(list)
            .setTitle("Select author or committer")
            .setResizable(true)
            .setDimensionServiceKey("Git.Select user")
            .setItemChoosenCallback(new Runnable() {
              public void run() {
                if (list.getSelectedIndices().length > 0) {
                  continuation.consume((String) list.getSelectedValue());
                }
              }
            })
            .createPopup().showInBestPositionFor(dc);
  }
}
