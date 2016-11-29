package org.zmlx.hg4idea.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.util.HgUtil;

import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;

/**
 * HgSetExecutablePathPanel is a {@link com.intellij.openapi.ui.TextFieldWithBrowseButton}, which opens a file chooser for hg executable
 * and checks validity of the selected file to be an hg executable.
 */
class HgSetExecutablePathPanel extends TextFieldWithBrowseButton {

  private final Set<ActionListener> myOkListeners = new HashSet<>();

  HgSetExecutablePathPanel(final HgProjectSettings projectSettings) {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        String path = files[0].getPath();
        if (!HgUtil.isExecutableValid(path)) {
          throw new ConfigurationException(HgVcsMessages.message("hg4idea.configuration.executable.error", path));
        }
        for (ActionListener okListener : myOkListeners) {
          okListener.actionPerformed(null);
        }
      }
    };
    addBrowseFolderListener(HgVcsMessages.message("hg4idea.configuration.title"), HgVcsMessages.message("hg4idea.configuration.description"), null, descriptor);
  }

  /**
   * Adds a listener which will be called when file chooser dialog is closed successfully.
   */
  void addOKListener(ActionListener listener) {
    myOkListeners.add(listener);
  }

}
