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
package com.intellij.cvsSupport2.cvsoperations.cvsWatch.ui;

import com.intellij.CvsBundle;
import com.intellij.openapi.ui.DialogWrapper;
import org.netbeans.lib.cvsclient.command.Watch;

import javax.swing.*;

/**
 * author: lesya
 */
public class WatcherDialog extends DialogWrapper{
  private JPanel myPanel;
  private JComboBox<Watch> myWatchingActions;
  public WatcherDialog(Watch defaultWatch, String title) {
    super(true);

    myWatchingActions.addItem(Watch.ALL);
    myWatchingActions.addItem(Watch.EDIT);
    myWatchingActions.addItem(Watch.UNEDIT);
    myWatchingActions.addItem(Watch.COMMIT);

    myWatchingActions.setSelectedItem(defaultWatch);

    setTitle(CvsBundle.message("dialog.title.watchers.or.editors.settings", title));

    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Watch getWatch(){
    return (Watch)myWatchingActions.getSelectedItem();
  }
}
