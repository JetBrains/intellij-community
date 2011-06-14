/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.impl.EditorCopyAction;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.notification.impl.NotificationsConfigurable;
import com.intellij.notification.impl.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.EditorPopupHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class EventLog implements Notifications {
  public static final String LOG_REQUESTOR = "Internal log requestor";
  public static final String LOG_TOOL_WINDOW_ID = "Event Log";
  private final LogModel myModel = new LogModel(null);

  public EventLog() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, this);
  }

  @Override
  public void notify(@NotNull Notification notification) {
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 0) {
      myModel.addNotification(notification);
    }
    for (Project p : openProjects) {
      getProjectComponent(p).printNotification(notification);
    }
  }

  public static void expire(@NotNull Notification notification) {
    getApplicationComponent().myModel.removeNotification(notification);
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      getProjectComponent(p).myProjectModel.removeNotification(notification);
    }
  }

  private static EventLog getApplicationComponent() {
    return ApplicationManager.getApplication().getComponent(EventLog.class);
  }

  @Override
  public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  @NotNull
  public static LogModel getLogModel(@Nullable Project project) {
    return project != null ? getProjectComponent(project).myProjectModel : getApplicationComponent().myModel;
  }

  @Nullable
  public static Notification getStatusMessage(@Nullable Project project) {
    return getLogModel(project).getStatusMessage();
  }

  public static Pair<String, Boolean> formatForLog(final Notification notification) {
    boolean showLink = notification.getListener() != null;
    String content = notification.getContent();
    String mainText = notification.getTitle();
    if (StringUtil.isNotEmpty(content) && !content.startsWith("<")) {
      if (StringUtil.isNotEmpty(mainText)) {
        mainText += ": ";
      }
      mainText += content;
    }

    int nlIndex = eolIndex(mainText);
    if (nlIndex >= 0) {
      mainText = mainText.substring(0, nlIndex);
      showLink = true;
    }

    mainText = mainText.replaceAll("<[^>]*>", "");
    return Pair.create(mainText, showLink);
  }

  private static int eolIndex(String mainText) {
    int nlIndex = mainText.indexOf("<br>");
    if (nlIndex < 0) nlIndex = mainText.indexOf("<br/>");
    if (nlIndex < 0) nlIndex = mainText.indexOf("\n");
    return nlIndex;
  }

  public static boolean isEventLogVisible(Project project) {
    final ToolWindow window = getEventLog(project);
    return window != null && window.isVisible();
  }

  @Nullable
  public static ToolWindow getEventLog(Project project) {
    return project == null ? null : ToolWindowManager.getInstance(project).getToolWindow(LOG_TOOL_WINDOW_ID);
  }

  public static class ProjectTracker extends AbstractProjectComponent {
    private volatile Editor myLogEditor;
    private volatile EditorHyperlinkSupport myHyperlinkSupport;
    private List<Notification> myInitial = new CopyOnWriteArrayList<Notification>();
    private final LogModel myProjectModel;

    public ProjectTracker(final Project project) {
      super(project);

      myProjectModel = new LogModel(project);

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return;
      }

      for (Notification notification : getApplicationComponent().myModel.takeNotifications()) {
        printNotification(notification);
      }

      project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, new Notifications() {
        @Override
        public void notify(@NotNull Notification notification) {
          printNotification(notification);
        }

        @Override
        public void register(@NotNull String groupDisplayType, @NotNull NotificationDisplayType defaultDisplayType) {
        }
      });

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (project.isDisposed()) {
            return;
          }

          createEditor(project);

          for (Notification notification : myInitial) {
            printNotification(notification);
          }
          myInitial.clear();
        }
      });
    }

    private void createEditor(Project project) {
      myLogEditor = setupConsoleEditor(project, false);
      myLogEditor.addEditorMouseListener(new EditorPopupHandler() {
        public void invokePopup(final EditorMouseEvent event) {
          final ActionManager actionManager = ActionManager.getInstance();
          final ActionPopupMenu menu = actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, createPopupActions(actionManager));
          final MouseEvent mouseEvent = event.getMouseEvent();
          menu.getComponent().show(mouseEvent.getComponent(), mouseEvent.getX(), mouseEvent.getY());
        }
      });
      myHyperlinkSupport = new EditorHyperlinkSupport(myLogEditor, project);
    }

    private static DefaultActionGroup createPopupActions(ActionManager actionManager) {
      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new DumbAwareAction("Clear All") {
        @Override
        public void update(AnActionEvent e) {
          final boolean enabled = e.getData(PlatformDataKeys.EDITOR) != null;
          e.getPresentation().setEnabled(enabled);
          e.getPresentation().setVisible(enabled);
        }

        public void actionPerformed(final AnActionEvent e) {
          final Editor editor = e.getData(PlatformDataKeys.EDITOR);
          if (editor != null) {
            editor.getDocument().deleteString(0, editor.getDocument().getTextLength());
          }
        }
      });
      group.add(new EditorCopyAction());
      group.addSeparator();
      group.add(actionManager.getAction(IdeActions.ACTION_COMPARE_CLIPBOARD_WITH_SELECTION));
      return group;
    }

    @Override
    public void projectClosed() {
      Editor logEditor = myLogEditor;
      if (logEditor != null) {
        EditorFactory.getInstance().releaseEditor(logEditor);
      }

      getApplicationComponent().myModel.setStatusMessage(null);
    }

    private void printNotification(final Notification notification) {
      final Editor logEditor = myLogEditor;
      if (logEditor == null) {
        myInitial.add(notification);
        return;
      }

      if (!NotificationsConfiguration.getSettings(notification.getGroupId()).isShouldLog()) {
        return;
      }

      myProjectModel.addNotification(notification);

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!logEditor.isDisposed()) {
            doPrintNotification(logEditor, logEditor.getDocument(), notification);
          }
        }
      });
    }

    private void doPrintNotification(Editor logEditor, Document document, final Notification notification) {
      boolean scroll = document.getTextLength() == logEditor.getCaretModel().getOffset();

      append(document, DateFormat.getTimeInstance(DateFormat.MEDIUM).format(notification.getCreationTime()) + " ");

      Pair<String, Boolean> pair = formatForLog(notification);

      final NotificationType type = notification.getType();
      ConsoleViewContentType contentType = type == NotificationType.ERROR
                                           ? ConsoleViewContentType.ERROR_OUTPUT
                                           : type == NotificationType.INFORMATION
                                             ? ConsoleViewContentType.NORMAL_OUTPUT
                                             : ConsoleViewContentType.WARNING_OUTPUT;

      int msgStart = document.getTextLength();
      append(document, pair.first);
      logEditor.getMarkupModel().addRangeHighlighter(msgStart, document.getTextLength(), HighlighterLayer.CARET_ROW + 1, contentType.getAttributes(), HighlighterTargetArea.EXACT_RANGE);

      if (pair.second) {
        String s = " ";
        append(document, s);

        int linkStart = document.getTextLength();
        append(document, "more");
        myHyperlinkSupport.addHyperlink(linkStart, document.getTextLength(), null, new HyperlinkInfo() {
          @Override
          public void navigate(Project project) {
            Balloon balloon = notification.getBalloon();
            if (balloon != null) {
              balloon.hide();
            }

            NotificationsManagerImpl.notifyByBalloon(notification, NotificationDisplayType.STICKY_BALLOON, project);
          }
        });

        append(document, " ");
      }
      append(document, "\n");

      if (scroll) {
        logEditor.getCaretModel().moveToOffset(document.getTextLength());
        logEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    }

    private static void append(Document document, String s) {
      document.insertString(document.getTextLength(), s);
    }
  }

  private static ProjectTracker getProjectComponent(Project project) {
    return project.getComponent(ProjectTracker.class);
  }
  public static class FactoryItself implements ToolWindowFactory, DumbAware {
    public void createToolWindowContent(final Project project, ToolWindow toolWindow) {
      final Editor editor = getProjectComponent(project).myLogEditor;

      SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);
      panel.setContent(editor.getComponent());

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new DumbAwareAction("Settings", "Edit notification settings", IconLoader.getIcon("/general/secondaryGroup.png")) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          ShowSettingsUtil.getInstance().editConfigurable(project, new NotificationsConfigurable());
        }
      });
      group.add(new ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.CONSOLE) {
        @Override
        protected Editor getEditor(AnActionEvent e) {
          return editor;
        }
      });
      group.add(new ScrollToTheEndToolbarAction(editor));

      ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false);
      toolbar.setTargetComponent(panel);
      panel.setToolbar(toolbar.getComponent());

      final Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "", false);
      toolWindow.getContentManager().addContent(content);
    }

  }

  public static EditorEx setupConsoleEditor(Project project, final boolean foldingOutlineShown) {
    EditorEx editor = (EditorEx) EditorFactory.getInstance().createViewer(((EditorFactoryImpl)EditorFactory.getInstance()).createDocument(true), project);
    editor.setSoftWrapAppliancePlace(SoftWrapAppliancePlaces.CONSOLE);

    final EditorSettings editorSettings = editor.getSettings();
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(foldingOutlineShown);
    editorSettings.setAdditionalPageAtBottom(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(0);

    final DelegateColorScheme scheme = new DelegateColorScheme(editor.getColorsScheme()) {
      @Override
      public Color getDefaultBackground() {
        final Color color = getColor(ConsoleViewContentType.CONSOLE_BACKGROUND_KEY);
        return color == null ? super.getDefaultBackground() : color;
      }
    };
    editor.setColorsScheme(scheme);
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);
    scheme.setColor(EditorColors.RIGHT_MARGIN_COLOR, null);
    return editor;
  }


}
