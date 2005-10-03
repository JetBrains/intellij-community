package com.intellij.cvsSupport2.cvsoperations.cvsWatch.ui;

import com.intellij.openapi.ui.DialogWrapper;
import org.netbeans.lib.cvsclient.command.Watch;

import javax.swing.*;
import java.text.MessageFormat;

/**
 * author: lesya
 */
public class WatcherDialog extends DialogWrapper{
  private JPanel myPanel;
  private JComboBox myWatchingActions;
  public WatcherDialog(Watch defaultWatch, String title) {
    super(true);

    myWatchingActions.addItem(Watch.ALL);
    myWatchingActions.addItem(Watch.EDIT);
    myWatchingActions.addItem(Watch.UNEDIT);
    myWatchingActions.addItem(Watch.COMMIT);

    myWatchingActions.setSelectedItem(defaultWatch);

    setTitle(com.intellij.CvsBundle.message("dialog.title.watchers.or.editors.settings", title));

    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Watch getWatch(){
    return (Watch)myWatchingActions.getSelectedItem();
  }
}
