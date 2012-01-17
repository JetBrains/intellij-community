package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.RichTextControlBuilder;

import javax.swing.*;
import java.awt.*;

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
  private final JComponent myNonLinkedInfoPanel;
  
  protected GradleToolWindowPanel(@NotNull String place) {
    super(true);
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(TOOL_WINDOW_TOOLBAR_ID);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(place, actionGroup, true);
    setToolbar(actionToolbar.getComponent());
    myNonLinkedInfoPanel = initContent();
    setContent(myContent);
  }

  private JComponent initContent() {
    final JComponent payloadControl = buildContent();
    myContent.add(ScrollPaneFactory.createScrollPane(payloadControl), CONTENT_CARD_NAME);
    RichTextControlBuilder builder = new RichTextControlBuilder();
    builder.setBackgroundColor(payloadControl.getBackground());
    builder.setForegroundColor(payloadControl.getForeground());
    builder.setFont(payloadControl.getFont());
    builder.setText(GradleBundle.message("gradle.toolwindow.text.no.linked.project"));
    final JComponent result = builder.build();
    myContent.add(result, NON_LINKED_CARD_NAME);
    myLayout.show(myContent, NON_LINKED_CARD_NAME);
    return result;
  }

  /**
   * @return    GUI control to be displayed at the current tab
   */
  @NotNull
  protected abstract JComponent buildContent();
}
