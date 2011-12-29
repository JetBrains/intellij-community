package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Base class for high-level Gradle GUI controls used at the Gradle tool window. The basic idea is to encapsulate the same features in
 * this class and allow to extend it via <code>Template Method</code> pattern. The shared features are listed below:
 * <pre>
 * <ul>
 *   <li>provide common actions at the toolbar;</li>
 *   <li>show info control when no gradle project is linked to the current IntelliJ IDEA project;</li>
 * </ul>
 * </pre>
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 12/26/11 5:19 PM
 */
public abstract class GradleToolWindowPanel extends SimpleToolWindowPanel {

  private static final String NON_LINKED_CARD_NAME   = "NON_LINKED";
  private static final String CONTENT_CARD_NAME      = "CONTENT";
  private static final String LINK_BUTTON_MARKER     = "{link}";
  private static final String TOOL_WINDOW_TOOLBAR_ID = "Gradle.ChangeActionsToolbar";
  private static final String LINK_PROJECT_ACTION_ID = "Gradle.LinkToProject";
  
  /** Show info control when no gradle project is linked, using {@link CardLayout} for that. */
  private final CardLayout myLayout             = new CardLayout();
  /** Top-level container, managed by the card layout. */
  private final JPanel     myContent            = new JPanel(myLayout);
  /** Control to show when no gradle project is linked to the current IntelliJ IDEA project. */
  private final JPanel     myNonLinkedInfoPanel = new JPanel();
  
  protected GradleToolWindowPanel(@NotNull String place) {
    super(true);
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(TOOL_WINDOW_TOOLBAR_ID);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(place, actionGroup, true);
    setToolbar(actionToolbar.getComponent());
    initContent();
    setContent(myContent);
  }

  private void initContent() {
    final JComponent payloadControl = buildContent();
    myContent.add(ScrollPaneFactory.createScrollPane(payloadControl), CONTENT_CARD_NAME);
    
    myNonLinkedInfoPanel.setLayout(new GridBagLayout());
    final Color background = payloadControl.getBackground();
    myNonLinkedInfoPanel.setBackground(background);
    List<JComponent> rowComponents = new ArrayList<JComponent>();
    final String sentence = GradleBundle.message("gradle.toolwindow.text.no.linked.project");
    for (String s : StringUtil.tokenize(new StringTokenizer(sentence, " \n", true))) {
      if (s.isEmpty()) {
        continue;
      }
      if (LINK_BUTTON_MARKER.equals(s)) {
        final ActionManager actionManager = ActionManager.getInstance();
        final AnAction action = actionManager.getAction(LINK_PROJECT_ACTION_ID);
        ActionButton button= new ActionButton(
          action, action.getTemplatePresentation().clone(), GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE, new Dimension(0, 0))
        {
          @Override
          protected void paintButtonLook(Graphics g) {
            // Don't draw border at the inline button.
            ActionButtonLook look = getButtonLook();
            look.paintBackground(g, this);
            look.paintIcon(g, this, getIcon());
          }
        };
        rowComponents.add(button);
      }
      else if (s.contains("\n")) {
        addRow(rowComponents, background);
        rowComponents.clear();
      }
      else {
        final JLabel label = new JLabel(s);
        label.setForeground(payloadControl.getForeground());
        label.setBackground(background);
        label.setFont(payloadControl.getFont());
        rowComponents.add(label);
      }
    }
    if (!rowComponents.isEmpty()) {
      addRow(rowComponents, background);
    }
    
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.weighty = 1;
    constraints.fill = GridBagConstraints.VERTICAL;
    myNonLinkedInfoPanel.add(Box.createVerticalStrut(1), constraints);
    myContent.add(myNonLinkedInfoPanel, NON_LINKED_CARD_NAME);
    
    myLayout.show(myContent, NON_LINKED_CARD_NAME);
  }

  private void addRow(@NotNull Collection<JComponent> rowComponents, @NotNull Color backgroundColor) {
    JPanel row = new MultiRowFlowPanel(FlowLayout.CENTER, 0, 3);
    row.setBackground(backgroundColor);
    if (rowComponents.isEmpty()) {
      row.add(new JLabel(" "));
    }
    else {
      for (JComponent component : rowComponents) {
        row.add(component);
      }
    }
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets.top = 3;
    myNonLinkedInfoPanel.add(row, constraints);
  }

  /**
   * @return    GUI control to be displayed at the current tab
   */
  @NotNull
  protected abstract JComponent buildContent();
}
