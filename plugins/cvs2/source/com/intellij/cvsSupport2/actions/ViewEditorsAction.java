package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsHandler;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorInfo;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.EditorsOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui.EditorsPanel;
import com.intellij.cvsSupport2.ui.CvsTabbedWindow;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.ui.Messages;

import java.util.List;

/**
 * author: lesya
 */
public class ViewEditorsAction extends AsbtractActionFromEditGroup {
  private EditorsOperation myEditorsOperation;

  protected String getTitle(VcsContext context) {
    return "View Editors";
  }

  protected CvsHandler getCvsHandler(CvsContext context) {
    myEditorsOperation = new EditorsOperation(context.getSelectedFiles());
    return new CommandCvsHandler("Veiw Editors", myEditorsOperation);
  }

  protected void onActionPerformed(CvsContext context,
                                   CvsTabbedWindow tabbedWindow,
                                   boolean successfully,
                                   CvsHandler handler) {
    super.onActionPerformed(context, tabbedWindow, successfully, handler);
    if (successfully){
      List<EditorInfo> editors = myEditorsOperation.getEditors();
      String filePath = CvsVfsUtil.getFileFor(context.getSelectedFile()).getAbsolutePath();
      if (editors.isEmpty()){
        Messages.showMessageDialog("There are no editors for " + filePath, "Editors", Messages.getInformationIcon());
      } else {
        tabbedWindow.addTab("Editors for " + filePath, new EditorsPanel(editors), true, true, true, true, "cvs.editors");
        tabbedWindow.ensureVisible(context.getProject());
      }
    }
  }
}
