package gitlog;

import com.intellij.ui.components.JBLabel;

import javax.swing.*;
import java.awt.*;

/**
 * @author Kirill Likhodedov
 */
public class MainPanel extends JPanel {

  MainPanel() {
    super(new BorderLayout());
    add(new JBLabel("Test"));
  }

}
