package gitlog;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

/**
 * @author Kirill Likhodedov
 */
public class MainPanel extends JPanel {

  MainPanel(Project project) {
    super(new BorderLayout());

    GitLogComponent gitLogComponent = ServiceManager.getService(project, GitLogComponent.class);
    add(gitLogComponent.getMainGitAlkComponent());
  }

}
