// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.UniqueNameBuilder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ClickListener;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.IconUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author max
 */
public class RecentProjectPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(RecentProjectPanel.class);

  public static final String RECENT_PROJECTS_LABEL = "Recent Projects";

  protected final JBList<AnAction> myList;
  protected final UniqueNameBuilder<ReopenProjectAction> myPathShortener;
  protected AnAction removeRecentProjectAction;
  protected Set<ReopenProjectAction> projectsWithLongPaths = new HashSet<>();
  protected FilePathChecker myChecker;
  private int myHoverIndex = -1;

  public RecentProjectPanel(@NotNull Disposable parentDisposable) {
    super(new BorderLayout());

    List<AnAction> recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(false, isUseGroups());

    myPathShortener = new UniqueNameBuilder<>(SystemProperties.getUserHome(), File.separator, 40);
    Collection<String> pathsToCheck = new HashSet<>();
    for (AnAction action : recentProjectActions) {
      if (action instanceof ReopenProjectAction) {
        ReopenProjectAction item = (ReopenProjectAction)action;
        myPathShortener.addPath(item, item.getProjectPath());
        pathsToCheck.add(item.getProjectPath());
      }
    }

    myList = createList(recentProjectActions.toArray(AnAction.EMPTY_ARRAY), getPreferredScrollableViewportSize());
    myList.setCellRenderer(createRenderer(myPathShortener));

    if (Registry.is("autocheck.availability.welcome.screen.projects")) {
      myChecker = new FilePathChecker(new Runnable() {
        @Override
        public void run() {
          if (myList.isShowing()) {
            myList.revalidate();
            myList.repaint();
          }
        }
      }, pathsToCheck);
      Disposer.register(parentDisposable, myChecker);
    }

    new ClickListener(){
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex >= 0) {
          Rectangle cellBounds = myList.getCellBounds(selectedIndex, selectedIndex);
          if (cellBounds.contains(event.getPoint())) {
            AnAction selection = myList.getSelectedValue();
            if (selection != null) {
              AnAction selectedAction = performSelectedAction(event, selection);
              // remove action from list if needed
              if (selectedAction instanceof ReopenProjectAction) {
                if (((ReopenProjectAction)selectedAction).isRemoved()) {
                  ListUtil.removeSelectedItems(myList);
                }
              }
            }
          }
        }
        return true;
      }
    }.installOn(myList);

    myList.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<AnAction> selectedValues = myList.getSelectedValuesList();
        if (selectedValues != null) {
          for (AnAction selectedAction : selectedValues) {
            if (selectedAction != null) {
              InputEvent event = new KeyEvent(myList, KeyEvent.KEY_PRESSED, e.getWhen(), e.getModifiers(), KeyEvent.VK_ENTER, '\r');
              performSelectedAction(event, selectedAction);
            }
          }
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    removeRecentProjectAction = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeRecentProject();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true);
      }
    };
    removeRecentProjectAction.registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), myList, parentDisposable);

    addMouseMotionListener();

    myList.setSelectedIndex(0);

    JBScrollPane scroll
      = new JBScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(null);

    JComponent list = recentProjectActions.isEmpty()
                      ? myList
                      : ListWithFilter.wrap(myList, scroll, o -> {
                        if (o instanceof ReopenProjectAction) {
                          ReopenProjectAction item = (ReopenProjectAction)o;
                          String home = SystemProperties.getUserHome();
                          String path = item.getProjectPath();
                          if (FileUtil.startsWith(path, home)) {
                            path = path.substring(home.length());
                          }
                          return item.getProjectName() + " " + path;
                        } else if (o instanceof ProjectGroupActionGroup) {
                          return ((ProjectGroupActionGroup)o).getGroup().getName();
                        }
                        return o.toString();
                      });
    add(list, BorderLayout.CENTER);

    JPanel title = createTitle();

    if (title != null) {
      add(title, BorderLayout.NORTH);
    }

    setBorder(new LineBorder(WelcomeScreenColors.BORDER_COLOR));
  }

  @NotNull
  private AnAction performSelectedAction(@NotNull InputEvent event, AnAction selection) {
    String actionPlace = UIUtil.uiParents(myList, true).filter(FlatWelcomeFrame.class).isEmpty() ? ActionPlaces.POPUP : ActionPlaces.WELCOME_SCREEN;
    AnActionEvent actionEvent = AnActionEvent
      .createFromInputEvent(event, actionPlace, selection.getTemplatePresentation(),
                            DataManager.getInstance().getDataContext(myList), false, false);
    ActionUtil.performActionDumbAwareWithCallbacks(selection, actionEvent, actionEvent.getDataContext());
    return selection;
  }

  private void removeRecentProject() {
    List<AnAction> selection = myList.getSelectedValuesList();
    if (selection == null || selection.isEmpty()) {
      return;
    }

    int rc = Messages.showOkCancelDialog(
      this,
      IdeBundle.message("dialog.message.remove.0.from.recent.projects.list",
                        StringUtil.join(selection, action -> action.getTemplatePresentation().getText(), "'\n'")),
      IdeBundle.message("dialog.title.remove.recent.project"),
      CommonBundle.getOkButtonText(), CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
    if (rc == Messages.OK) {
      for (AnAction projectAction : selection) {
        removeRecentProjectElement(projectAction);
      }
      ListUtil.removeSelectedItems(myList);
    }
  }

  protected boolean isPathValid(String path) {
    return myChecker == null || myChecker.isValid(path);
  }

  protected static void removeRecentProjectElement(@NotNull Object element) {
    RecentProjectsManager manager = RecentProjectsManager.getInstance();
    if (element instanceof ReopenProjectAction) {
      manager.removePath(((ReopenProjectAction)element).getProjectPath());
    }
    else if (element instanceof ProjectGroupActionGroup) {
      manager.removeGroup(((ProjectGroupActionGroup)element).getGroup());
    }
  }

  protected boolean isUseGroups() {
    return false;
  }

  protected Dimension getPreferredScrollableViewportSize() {
    return JBUI.size(250, 400);
  }

  protected void addMouseMotionListener() {
    MouseAdapter mouseAdapter = new MouseAdapter() {
      boolean myIsEngaged = false;
      @Override
      public void mouseMoved(MouseEvent e) {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focusOwner == null) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
        }
        if (myList.getSelectedIndices().length > 1) {
          return;
        }

        if (!myIsEngaged || UIUtil.isSelectionButtonDown(e) || focusOwner instanceof JRootPane) {
          myIsEngaged = true;
          return;
        }

        Point point = e.getPoint();
        int index = myList.locationToIndex(point);
        myList.setSelectedIndex(index);

        Rectangle cellBounds = myList.getCellBounds(index, index);
        if (cellBounds != null && cellBounds.contains(point)) {
          UIUtil.setCursor(myList, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          myHoverIndex = index;
          myList.repaint(cellBounds);
        }
        else {
          UIUtil.setCursor(myList, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
          myHoverIndex = -1;
          myList.repaint();
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myHoverIndex = -1;
        myList.repaint();
      }
    };

    myList.addMouseMotionListener(mouseAdapter);
    myList.addMouseListener(mouseAdapter);
  }

  protected JBList<AnAction> createList(AnAction[] recentProjectActions, Dimension size) {
    return new MyList(size, recentProjectActions);
  }

  protected ListCellRenderer<AnAction> createRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
    return new RecentProjectItemRenderer();
  }

  @Nullable
  protected JPanel createTitle() {
    JPanel title = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, JBUIScale.scale(28));
      }
    };
    title.setBorder(new BottomLineBorder());

    JLabel titleLabel = new JLabel(RECENT_PROJECTS_LABEL);
    title.add(titleLabel);
    titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
    titleLabel.setForeground(WelcomeScreenColors.CAPTION_FOREGROUND);
    title.setBackground(WelcomeScreenColors.CAPTION_BACKGROUND);
    return title;
  }

  private class MyList extends JBList<AnAction> {
    private final Dimension mySize;
    private Point myMousePoint;

    private MyList(Dimension size, AnAction @NotNull [] listData) {
      super(listData);
      mySize = size;
      setExpandableItemsEnabled(false);
      setEmptyText(IdeBundle.message("empty.text.no.project.open.yet"));
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      getAccessibleContext().setAccessibleName(RECENT_PROJECTS_LABEL);
      final MouseHandler handler = new MouseHandler();
      addMouseListener(handler);
      addMouseMotionListener(handler);
    }

    public Rectangle getCloseIconRect(int index) {
      final Rectangle bounds = getCellBounds(index, index);
      Icon icon = toSize(AllIcons.Welcome.Project.Remove);
      return new Rectangle(bounds.width - icon.getIconWidth() - JBUIScale.scale(10),
                           bounds.y + (bounds.height - icon.getIconHeight()) / 2,
                           icon.getIconWidth(), icon.getIconHeight());
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (myMousePoint != null) {
        final int index = locationToIndex(myMousePoint);
        if (index != -1) {
          final Rectangle iconRect = getCloseIconRect(index);
          Icon icon = toSize(iconRect.contains(myMousePoint) ? AllIcons.Welcome.Project.Remove_hover : AllIcons.Welcome.Project.Remove);
          icon.paintIcon(this, g, iconRect.x, iconRect.y);
        }
      }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      final int i = event != null ? locationToIndex(event.getPoint()) : -1;
      if (i != -1) {
        final Object elem = getModel().getElementAt(i);
        if (elem instanceof ReopenProjectAction) {
          @SystemIndependent String path = ((ReopenProjectAction)elem).getProjectPath();
          boolean valid = isPathValid(path);
          if (!valid || RecentProjectPanel.this.projectsWithLongPaths.contains(elem)) {
            String suffix = valid ? "" : " (unavailable)";
            return PathUtil.toSystemDependentName(path) + suffix;
          }
        }
      }
      return super.getToolTipText(event);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return mySize == null ? super.getPreferredScrollableViewportSize() : mySize;
    }

    class MouseHandler extends MouseAdapter {
      @Override
      public void mouseEntered(MouseEvent e) {
        myMousePoint = e != null ? e.getPoint() : null;
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myMousePoint = null;
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        myMousePoint = e != null ? e.getPoint() : null;
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        Point point = e != null ? e.getPoint() : null;
        int index = point != null ? locationToIndex(point) : -1;
        if (index == -1 || !getCloseIconRect(index).contains(point)) {
          return;
        }

        e.consume();
        Object element = getModel().getElementAt(index);
        if (element instanceof ProjectGroupActionGroup) {
          ProjectGroup group = ((ProjectGroupActionGroup)element).getGroup();
          if (group.isTutorials()) {
            removeTutorialChildren(group);
          }
        }
        removeRecentProjectElement(element);
        ListUtil.removeSelectedItems(MyList.this);
      }

      private void removeTutorialChildren(ProjectGroup group) {
        int currentIndex = MyList.this.getSelectedIndex();
        List<String> projects = group.getProjects();
        final int[] childIndices = new int[projects.size()];
        for (int i = 0; i < projects.size(); i++) {
          childIndices[i] = currentIndex + 1;
          currentIndex++;
        }
        ListUtil.removeIndices(MyList.this, childIndices);
        ApplicationManager.getApplication().invokeLater(() -> {
          String title = IdeBundle.message("notification.title.tutorials.have.been.removed.from.recent.list");
          String content = IdeBundle.message("notification.content.you.can.still.find.them.in.help.menu");
          Notifications.Bus.notify(
            new Notification(NotificationGroup.createIdWithTitle("Tutorials", IdeBundle.message("notification.group.tutorials")), title,
                             content, NotificationType.INFORMATION));
        });
      }
    }
  }

  protected class RecentProjectItemRenderer extends JPanel implements ListCellRenderer<AnAction> {
    protected final JLabel myName = new JLabel();
    protected final JLabel myPath = new JLabel();
    protected boolean myHovered;

    /** @deprecated use the default constructor */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
    protected RecentProjectItemRenderer(@SuppressWarnings("unused") UniqueNameBuilder<ReopenProjectAction> pathShortener) {
      this();
    }

    protected RecentProjectItemRenderer() {
      super(new VerticalFlowLayout());

      myPath.setFont(JBUI.Fonts.label(SystemInfo.isMac ? 10f : 11f));
      setFocusable(true);
      layoutComponents();
    }

    protected void layoutComponents() {
      add(myName);
      add(myPath);
    }

    protected Color getListBackground(boolean isSelected, boolean hasFocus) {
      return UIUtil.getListBackground(isSelected, true);
    }

    protected Color getListForeground(boolean isSelected, boolean hasFocus) {
      return UIUtil.getListForeground(isSelected, true);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends AnAction> list, AnAction value, int index, boolean selected, boolean focused) {
      myHovered = myHoverIndex == index;
      Color fore = getListForeground(selected, list.hasFocus());
      Color back = getListBackground(selected, list.hasFocus());

      myName.setForeground(fore);
      myPath.setForeground(selected ? fore : UIUtil.getInactiveTextColor());

      setBackground(back);

      if (value instanceof ReopenProjectAction) {
        ReopenProjectAction item = (ReopenProjectAction)value;
        myName.setText(item.getTemplatePresentation().getText());
        myPath.setText(getTitle2Text(item, myPath, JBUIScale.scale(40)));
      }
      else if (value instanceof ProjectGroupActionGroup) {
        final ProjectGroupActionGroup group = (ProjectGroupActionGroup)value;
        myName.setText(group.getGroup().getName());
        myPath.setText("");
      }
      AccessibleContextUtil.setCombinedName(this, myName, " - ", myPath);
      AccessibleContextUtil.setCombinedDescription(this, myName, " - ", myPath);
      return this;
    }

    protected String getTitle2Text(ReopenProjectAction action, JComponent pathLabel, int leftOffset) {
      String fullText = action.getProjectPath();
      if (fullText == null || fullText.length() == 0) return " ";

      fullText = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(fullText), false);

      try {
        FontMetrics fm = pathLabel.getFontMetrics(pathLabel.getFont());
        int maxWidth = RecentProjectPanel.this.getWidth() - leftOffset - (int)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth() -
                       JBUIScale.scale(10);
        if (maxWidth > 0 && fm.stringWidth(fullText) > maxWidth) {
          return truncateDescription(fullText, fm, maxWidth, isTutorial(action));
        }
      } catch (Exception e) {
        LOG.error("Path label font: " + pathLabel.getFont());
        LOG.error("Panel width: " + RecentProjectPanel.this.getWidth());
        LOG.error(e);
      }

      return fullText;
    }

    private boolean isTutorial(ReopenProjectAction action) {
      List<ProjectGroup> groups = RecentProjectsManager.getInstance().getGroups();
      for (ProjectGroup group : groups) {
        if (!group.isTutorials()) {
          continue;
        }

        for (String project : group.getProjects()) {
          if(project.contains(action.getProjectPath()))
            return true;
        }
      }

      return false;
    }

    @NotNull
    private String truncateDescription(String fullText, FontMetrics fm, int maxWidth, boolean isTutorial) {
      if (isTutorial) {
        String tutorialTruncated = fullText;
        while (fm.stringWidth(tutorialTruncated) > maxWidth) {
          tutorialTruncated = tutorialTruncated.substring(0, tutorialTruncated.length() - 1);
        }
        return tutorialTruncated + "...";

      }

      int left = 1;
      int right = 1;
      int center = fullText.length() / 2;
      String s = fullText.substring(0, center - left) + "..." + fullText.substring(center + right);
      while (fm.stringWidth(s) > maxWidth) {
        if (left == right) {
          left++;
        } else {
          right++;
        }

        if (center - left < 0 || center + right >= fullText.length()) {
          return "";
        }
        s = fullText.substring(0, center - left) + "..." + fullText.substring(center + right);
      }
      return s;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.min(size.width, JBUIScale.scale(245)), size.height);
    }

    @NotNull
    @Override
    public Dimension getSize() {
      return getPreferredSize();
    }
  }

  private static class FilePathChecker implements Disposable, ApplicationActivationListener, PowerSaveMode.Listener {
    private static final int MIN_AUTO_UPDATE_MILLIS = 2500;
    private ScheduledExecutorService myService = null;
    private final Set<String> myInvalidPaths = Collections.synchronizedSet(new HashSet<>());

    private final Runnable myCallback;
    private final Collection<String> myPaths;

    FilePathChecker(Runnable callback, Collection<String> paths) {
      myCallback = callback;
      myPaths = paths;
      MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
      connection.subscribe(ApplicationActivationListener.TOPIC, this);
      connection.subscribe(PowerSaveMode.TOPIC, this);
      onAppStateChanged();
    }

    boolean isValid(String path) {
      return !myInvalidPaths.contains(path);
    }

    @Override
    public void applicationActivated(@NotNull IdeFrame ideFrame) {
      onAppStateChanged();
    }

    @Override
    public void delayedApplicationDeactivated(@NotNull Window ideFrame) {
      onAppStateChanged();
    }

    @Override
    public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
    }

    @Override
    public void powerSaveStateChanged() {
      onAppStateChanged();
    }

    private void onAppStateChanged() {
      boolean settingsAreOK = Registry.is("autocheck.availability.welcome.screen.projects") && !PowerSaveMode.isEnabled();
      boolean everythingIsOK = settingsAreOK && ApplicationManager.getApplication().isActive();
      if (myService == null && everythingIsOK) {
        myService = AppExecutorUtil.createBoundedScheduledExecutorService("CheckRecentProjectPaths Service", 2);
        for (String path : myPaths) {
          scheduleCheck(path, 0);
        }
        ApplicationManager.getApplication().invokeLater(myCallback);
      }
      if (myService != null && !everythingIsOK) {
        if (!settingsAreOK) {
          myInvalidPaths.clear();
        }
        if (!myService.isShutdown()) {
          myService.shutdown();
          myService = null;
        }
        ApplicationManager.getApplication().invokeLater(myCallback);
      }
    }


    @Override
    public void dispose() {
      if (myService != null) {
        myService.shutdownNow();
      }
    }

    private void scheduleCheck(String path, long delay) {
      if (myService == null || myService.isShutdown()) return;

      myService.schedule(() -> {
        final long startTime = System.currentTimeMillis();
        boolean pathIsValid;
        try {
          pathIsValid = !RecentProjectsManagerBase.isFileSystemPath(path) || isPathAvailable(path);
        }
        catch (Exception e) {
          pathIsValid = false;
        }
        if (myInvalidPaths.contains(path) == pathIsValid) {
          if (pathIsValid) {
            myInvalidPaths.remove(path);
          }
          else {
            myInvalidPaths.add(path);
          }
          ApplicationManager.getApplication().invokeLater(myCallback);
        }
        scheduleCheck(path, Math.max(MIN_AUTO_UPDATE_MILLIS, 10 * (System.currentTimeMillis() - startTime)));
      }, delay, TimeUnit.MILLISECONDS);
    }
  }

  private static boolean isPathAvailable(String pathStr) {
    Path path = Paths.get(pathStr), pathRoot = path.getRoot();
    if (pathRoot == null) return false;
    if (SystemInfo.isWindows && pathRoot.toString().startsWith("\\\\")) return true;
    for (Path fsRoot : pathRoot.getFileSystem().getRootDirectories()) {
      if (pathRoot.equals(fsRoot)) return Files.exists(path);
    }
    return false;
  }

  @NotNull
  private static Icon toSize(@NotNull Icon icon) {
    return IconUtil.toSize(icon,
                           (int)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth(),
                           (int)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getHeight());
  }
}