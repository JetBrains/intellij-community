package com.intellij.ide.commander;

import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.ContentFactory;

/**
 * @author yole
 */
public class CommanderToolWindowFactory implements ToolWindowFactory {
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    Commander commander = Commander.getInstance(project);
    toolWindow.getContentManager().addContent(ContentFactory.SERVICE.getInstance().createContent(commander, "", false));
  }
}
