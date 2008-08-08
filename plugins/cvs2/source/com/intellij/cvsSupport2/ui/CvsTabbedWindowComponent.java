package com.intellij.cvsSupport2.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class CvsTabbedWindowComponent extends JPanel implements DataProvider, CvsTabbedWindow.DeactivateListener {
  private final JComponent myComponent;
  private final ContentManager myContentManager;
  private Content myContent;
  private final boolean myAddToolbar;
  private final String myHelpId;


  public CvsTabbedWindowComponent(JComponent component, boolean addDefaultToolbar,
                                  @Nullable ActionGroup toolbarActions,
                                  ContentManager contentManager, String helpId) {
    super(new BorderLayout());
    myAddToolbar = addDefaultToolbar;
    myComponent = component;
    myContentManager = contentManager;
    myHelpId = helpId;

    add(myComponent, BorderLayout.CENTER);

    if (myAddToolbar) {
      DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
      actionGroup.add(new CloseAction());
      if (toolbarActions != null) {
        actionGroup.add(toolbarActions);
      }
      actionGroup.add(new HelpAction());
      add(ActionManager.getInstance().
          createActionToolbar("DefaultCvsComponentToolbar", actionGroup, false).getComponent(),
          BorderLayout.WEST);
    }
  }


  public Object getData(String dataId) {
    if (DataConstants.HELP_ID.equals(dataId)) {
      return myHelpId;
    }
    return null;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void setContent(Content content) {
    myContent = content;
  }

  public JComponent getShownComponent() {
    return myAddToolbar ? this : myComponent;
  }

  private class CloseAction extends AnAction {
    public CloseAction() {
      super(com.intellij.CvsBundle.message("close.tab.action.name"), "", IconLoader.getIcon("/actions/cancel.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      myContentManager.removeContent(myContent, true);

      deactivated();
    }
  }

  public void deactivated() {
    if (myComponent instanceof CvsTabbedWindow.DeactivateListener) {
      ((CvsTabbedWindow.DeactivateListener) myComponent).deactivated();
    }
  }

  private class HelpAction extends AnAction {
    public HelpAction() {
      super(CommonBundle.getHelpButtonText(), null, IconLoader.getIcon("/actions/help.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      HelpManager.getInstance().invokeHelp(myHelpId);
    }
  }
}
