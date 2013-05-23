package gitlog;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;

/**
 * @author Kirill Likhodedov
 */
public class GitLogToolWindowFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    MainPanel mainPanel = new MainPanel();
    final Content content = ContentFactory.SERVICE.getInstance().createContent(mainPanel, "", false);
    contentManager.addContent(content);
  }
}
