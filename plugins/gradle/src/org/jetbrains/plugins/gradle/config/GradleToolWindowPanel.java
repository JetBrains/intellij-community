package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.ui.RichTextControlBuilder;

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
  private static final String TOOL_WINDOW_TOOLBAR_ID = "Gradle.ChangeActionsToolbar";

  /** Show info control when no gradle project is linked, using {@link CardLayout} for that. */
  private final CardLayout myLayout  = new CardLayout();
  /** Top-level container, managed by the card layout. */
  private final JPanel     myContent = new JPanel(myLayout);

  private final Project myProject;
  
  protected GradleToolWindowPanel(@NotNull Project project, @NotNull String place) {
    super(true);
    myProject = project;
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(TOOL_WINDOW_TOOLBAR_ID);
    ActionToolbar actionToolbar = actionManager.createActionToolbar(place, actionGroup, true);
    setToolbar(actionToolbar.getComponent());
    initContent();
    setContent(myContent);
    update();

    MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(GradleConfigNotifier.TOPIC, new GradleConfigNotifier() {
      @Override
      public void onLinkedProjectPathChange(@Nullable String oldPath, @Nullable String newPath) {
        update();
      }
    });
  }

  private void initContent() {
    final JComponent payloadControl = buildContent();
    myContent.add(ScrollPaneFactory.createScrollPane(payloadControl), CONTENT_CARD_NAME);
    RichTextControlBuilder builder = new RichTextControlBuilder();
    builder.setBackgroundColor(payloadControl.getBackground());
    builder.setForegroundColor(payloadControl.getForeground());
    builder.setFont(payloadControl.getFont());
    builder.setText(GradleBundle.message("gradle.toolwindow.text.no.linked.project"));
    final JComponent noLinkedProjectControl = builder.build();
    myContent.add(noLinkedProjectControl, NON_LINKED_CARD_NAME);
  }

  /**
   * Asks current control to update its state.
   */
  public void update() {
    final GradleSettings settings = GradleSettings.getInstance(myProject);
    String cardToShow = settings.LINKED_PROJECT_FILE_PATH == null ? NON_LINKED_CARD_NAME : CONTENT_CARD_NAME;
    myLayout.show(myContent, cardToShow);
    
    updateContent();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * @return    GUI control to be displayed at the current tab
   */
  @NotNull
  protected abstract JComponent buildContent();
  
  /**
   * Callback for asking content control to update its state.
   */
  protected abstract void updateContent();
}
