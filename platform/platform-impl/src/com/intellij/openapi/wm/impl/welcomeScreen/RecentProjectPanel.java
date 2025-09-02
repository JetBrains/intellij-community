// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.CommonBundle;
import com.intellij.filename.UniqueNameBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.panel.ComponentPanelBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ClickListener;
import com.intellij.ui.IdeUICustomization;
import com.intellij.ui.ListUtil;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@ApiStatus.Internal
public class RecentProjectPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(RecentProjectPanel.class);

  private static final Supplier<@Nls String> RECENT_PROJECTS_LABEL = IdeUICustomization
    .getInstance()
    .projectMessagePointer("popup.title.recent.projects");

  protected final JBList<AnAction> myList;
  final Set<ReopenProjectAction> projectsWithLongPaths = new HashSet<>();
  protected final FilePathChecker myChecker;
  private int myHoverIndex = -1;

  RecentProjectPanel(@NotNull Disposable parentDisposable, boolean withSpeedSearch) {
    super(new BorderLayout());

    List<AnAction> recentProjectActions = RecentProjectListActionProvider.getInstance().getActions(false, isUseGroups());

    UniqueNameBuilder<ReopenProjectAction> pathShortener = new UniqueNameBuilder<>(SystemProperties.getUserHome(), File.separator);
    Collection<String> pathsToCheck = new HashSet<>();
    for (AnAction action : recentProjectActions) {
      if (action instanceof ReopenProjectAction item) {
        pathShortener.addPath(item, item.getProjectPath());
        pathsToCheck.add(item.getProjectPath());
      }
    }

    myList = createList(recentProjectActions.toArray(AnAction.EMPTY_ARRAY), getPreferredScrollableViewportSize());
    myList.setCellRenderer(createRenderer(pathShortener));

    if (Registry.is("autocheck.availability.welcome.screen.projects")) {
      myChecker = new FilePathChecker(() -> {
        if (myList.isShowing()) {
          myList.revalidate();
          myList.repaint();
        }
      }, pathsToCheck);
      Disposer.register(parentDisposable, myChecker);
    }
    else {
      myChecker = null;
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
              // remove action from a list if needed
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

    myList.registerKeyboardAction(e -> {
      List<AnAction> selectedValues = myList.getSelectedValuesList();
      if (selectedValues != null) {
        for (AnAction selectedAction : selectedValues) {
          if (selectedAction != null) {
            InputEvent event = new KeyEvent(myList, KeyEvent.KEY_PRESSED, e.getWhen(), e.getModifiers(), KeyEvent.VK_ENTER, '\r');
            performSelectedAction(event, selectedAction);
          }
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    AnAction removeRecentProjectAction = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeRecentProject();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(true);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };
    removeRecentProjectAction.registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), myList, parentDisposable);

    addMouseMotionListener();

    myList.setSelectedIndex(0);

    JBScrollPane scroll
      = new JBScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(JBUI.Borders.empty());

    boolean wrapListWithFiltered = !recentProjectActions.isEmpty() && withSpeedSearch;
    JComponent list = wrapListWithFiltered ? ListWithFilter.wrap(myList, scroll, createProjectNameFunction()) : myList;
    add(wrapListWithFiltered ? list : scroll, BorderLayout.CENTER);

    JPanel title = createTitle();

    if (title != null) {
      add(title, BorderLayout.NORTH);
    }

    setBorder(new LineBorder(WelcomeScreenColors.BORDER_COLOR));
  }

  private static Function<? super AnAction, String> createProjectNameFunction() {
    return o -> {
      if (o instanceof ReopenProjectAction) {
        return getProjectName((ReopenProjectAction)o);
      }
      else if (o instanceof ProjectGroupActionGroup) {
        return ((ProjectGroupActionGroup)o).getGroup().getName();
      }
      return o.toString();
    };
  }

  private static @NotNull String getProjectName(@NotNull ReopenProjectAction projectItem) {
    String home = SystemProperties.getUserHome();
    String path = projectItem.getProjectPath();
    if (FileUtil.startsWith(path, home)) {
      path = path.substring(home.length());
    }
    return projectItem.getProjectNameToDisplay() + " " + path;
  }

  private @NotNull AnAction performSelectedAction(@NotNull InputEvent event, AnAction selection) {
    String actionPlace = UIUtil.uiParents(myList, true).filter(FlatWelcomeFrame.class).isEmpty() ? ActionPlaces.POPUP : ActionPlaces.WELCOME_SCREEN;
    DataContext dataContext = DataManager.getInstance().getDataContext(myList);
    AnActionEvent actionEvent = AnActionEvent.createEvent(
      dataContext, selection.getTemplatePresentation().clone(),
      actionPlace, ActionUiKind.NONE, event);
    ActionUtil.performAction(selection, actionEvent);
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

  boolean isPathValid(String path) {
    return myChecker == null || myChecker.isValid(path);
  }

  private static void removeRecentProjectElement(@NotNull Object element) {
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
      boolean myIsEngaged;
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

  protected @Nullable JPanel createTitle() {
    JPanel title = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, JBUIScale.scale(28));
      }
    };
    title.setBorder(new BottomLineBorder());

    JLabel titleLabel = new JLabel(RECENT_PROJECTS_LABEL.get());
    title.add(titleLabel);
    titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
    titleLabel.setForeground(WelcomeScreenColors.CAPTION_FOREGROUND);
    title.setBackground(WelcomeScreenColors.CAPTION_BACKGROUND);
    return title;
  }

  private final class MyList extends JBList<AnAction> {
    private final Dimension mySize;
    private Point myMousePoint;

    private MyList(Dimension size, AnAction @NotNull [] listData) {
      super(listData);
      mySize = size;
      setExpandableItemsEnabled(false);
      setEmptyText(IdeBundle.message("empty.text.no.project.open.yet"));
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      getAccessibleContext().setAccessibleName(RECENT_PROJECTS_LABEL.get());
      final PopupHandler handler = new MyPopupMouseHandler();
      addMouseListener(handler);
      addMouseMotionListener(handler);
    }

    Rectangle getCloseIconRect(int index) {
      final Rectangle bounds = getCellBounds(index, index);
      Icon icon = toSize(AllIcons.Ide.Notification.Gear);
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
          Icon actionIcon = detectActionIcon(index, iconRect.contains(myMousePoint));
          Icon icon = toSize(actionIcon);
          icon.paintIcon(this, g, iconRect.x, iconRect.y);
        }
      }
    }

    private Icon detectActionIcon(int rowIndex, boolean hovered) {
      if (isProjectInvalid(rowIndex)) {
        return hovered ? AllIcons.Welcome.RecentProjects.RemoveHover : AllIcons.Welcome.RecentProjects.Remove;
      }
      return hovered ? AllIcons.Ide.Notification.GearHover : AllIcons.Ide.Notification.Gear;
    }

    private boolean isProjectInvalid(int listIndex) {
      AnAction action = myList.getModel().getElementAt(listIndex);
      if (action instanceof ReopenProjectAction) {
        return !isPathValid((((ReopenProjectAction)action).getProjectPath()));
      }
      return false;
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
            String suffix = valid ? "" : " " + IdeBundle.message("recent.project.unavailable");
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

    final class MyPopupMouseHandler extends PopupHandler {
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
        super.mouseReleased(e);
        if (e.isConsumed()) return;

        Point point = e.getPoint();
        int index = locationToIndex(point);
        if (index == -1 || !getCloseIconRect(index).contains(point)) return;
        if (isProjectInvalid(index)) {
          removeRecentProjectElement(getModel().getElementAt(index));
          ListUtil.removeItem(getModel(), index);
        }
        else {
          invokePopup(e.getComponent(), e.getX(), e.getY());
        }
        e.consume();
      }

      @Override
      public void invokePopup(Component comp, int x, int y) {
        final int index = locationToIndex(new Point(x, y));
        if (index != -1 && Arrays.binarySearch(getSelectedIndices(), index) < 0) {
          setSelectedIndex(index);
        }
        final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("WelcomeScreenRecentProjectActionGroup");
        if (group != null) {
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.WELCOME_SCREEN, group).getComponent().show(comp, x, y);
        }
      }
    }
  }

  protected class RecentProjectItemRenderer extends JPanel implements ListCellRenderer<AnAction> {
    protected final JLabel myName = new JLabel();
    protected final JLabel myPath = ComponentPanelBuilder.createNonWrappingCommentComponent("");
    boolean myHovered;

    RecentProjectItemRenderer() {
      super(new VerticalFlowLayout());
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
      myPath.setForeground(NamedColorUtil.getInactiveTextColor());

      setBackground(back);

      if (value instanceof ReopenProjectAction item) {
        myName.setText(item.getTemplatePresentation().getText());
        myPath.setText(getTitle2Text(item, myPath, JBUIScale.scale(40)));
      }
      else if (value instanceof ProjectGroupActionGroup group) {
        myName.setText(group.getGroup().getName());
        myPath.setText("");
      }
      AccessibleContextUtil.setCombinedName(this, myName, " - ", myPath);
      AccessibleContextUtil.setCombinedDescription(this, myName, " - ", myPath);
      return this;
    }

    @NlsSafe String getTitle2Text(ReopenProjectAction action, JComponent pathLabel, int leftOffset) {
      String fullText = action.getProjectPath();
      if (fullText.isEmpty()) {
        return " ";
      }

      fullText = FileUtil.getLocationRelativeToUserHome(PathUtil.toSystemDependentName(fullText), false);

      try {
        FontMetrics fm = pathLabel.getFontMetrics(pathLabel.getFont());
        int maxWidth = RecentProjectPanel.this.getWidth() - leftOffset - (int)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth() -
                       JBUIScale.scale(10);
        if (maxWidth > 0 && fm.stringWidth(fullText) > maxWidth) {
          return truncateDescription(fullText, fm, maxWidth, isTutorial(action));
        }
      }
      catch (Exception e) {
        LOG.error("Path label font: " + pathLabel.getFont());
        LOG.error("Panel width: " + RecentProjectPanel.this.getWidth());
        LOG.error(e);
      }

      return fullText;
    }

    private static boolean isTutorial(ReopenProjectAction action) {
      List<ProjectGroup> groups = RecentProjectsManager.getInstance().getGroups();
      for (ProjectGroup group : groups) {
        if (!group.isTutorials()) {
          continue;
        }

        for (String project : group.getProjects()) {
          if (project.contains(action.getProjectPath())) {
            return true;
          }
        }
      }

      return false;
    }

    private static @NotNull String truncateDescription(String fullText, FontMetrics fm, int maxWidth, boolean isTutorial) {
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

    @Override
    public @NotNull Dimension getSize() {
      return getPreferredSize();
    }
  }

  public static final class FilePathChecker implements Disposable, PowerSaveMode.Listener {
    private static final int MIN_AUTO_UPDATE_MILLIS = 2500;
    private ScheduledExecutorService service;
    private final Set<String> invalidPaths = Collections.synchronizedSet(new HashSet<>());

    private final Runnable callback;
    private final Collection<String> paths;

    public FilePathChecker(Runnable callback, Collection<String> paths) {
      this.callback = callback;
      this.paths = paths;
      MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
      connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
        @Override
        public void applicationActivated(@NotNull IdeFrame ideFrame) {
          onAppStateChanged();
        }

        @Override
        public void delayedApplicationDeactivated(@NotNull Window ideFrame) {
          onAppStateChanged();
        }
      });
      connection.subscribe(PowerSaveMode.TOPIC, this);
      onAppStateChanged();
    }

    public boolean isValid(String path) {
      return !invalidPaths.contains(path);
    }

    @Override
    public void powerSaveStateChanged() {
      onAppStateChanged();
    }

    private void onAppStateChanged() {
      boolean settingsAreOK = Registry.is("autocheck.availability.welcome.screen.projects") && !PowerSaveMode.isEnabled();
      boolean everythingIsOK = settingsAreOK && ApplicationManager.getApplication().isActive();
      synchronized (this) {
        if (service == null && everythingIsOK) {
          service = AppExecutorUtil.createBoundedScheduledExecutorService("CheckRecentProjectPaths Service", 2);
          for (String path : paths) {
            scheduleCheck(path, 0);
          }
          ApplicationManager.getApplication().invokeLater(callback);
        }
        if (service != null && !everythingIsOK) {
          if (!settingsAreOK) {
            invalidPaths.clear();
          }
          shutdown(false);
          ApplicationManager.getApplication().invokeLater(callback);
        }
      }
    }

    private synchronized void shutdown(boolean now) {
      if (service != null) {
        if (now) {
          service.shutdownNow();
        }
        else {
          service.shutdown();
        }
        service = null;
      }
    }

    @Override
    public void dispose() {
      shutdown(true);
    }

    private synchronized void scheduleCheck(String path, long delay) {
      if (service == null || service.isShutdown()) {
        return;
      }

      service.schedule(() -> {
        final long startTime = System.currentTimeMillis();
        boolean pathIsValid;
        try {
          pathIsValid = !RecentProjectsManagerBase.Companion.isFileSystemPath(path) || isPathAvailable(path);
        }
        catch (Exception e) {
          pathIsValid = false;
        }
        if (invalidPaths.contains(path) == pathIsValid) {
          if (pathIsValid) {
            invalidPaths.remove(path);
          }
          else {
            invalidPaths.add(path);
          }
          ApplicationManager.getApplication().invokeLater(callback);
        }
        scheduleCheck(path, Math.max(MIN_AUTO_UPDATE_MILLIS, 10 * (System.currentTimeMillis() - startTime)));
      }, delay, TimeUnit.MILLISECONDS);
    }
  }

  private static boolean isPathAvailable(String pathStr) {
    Path path = Paths.get(pathStr);
    Path pathRoot = path.getRoot();
    if (pathRoot == null) return false;
    if (SystemInfo.isWindows && pathRoot.toString().startsWith("\\\\")) return true;
    return ContainerUtil.find(pathRoot.getFileSystem().getRootDirectories(), pathRoot) != null && Files.exists(path);
  }

  private static @NotNull Icon toSize(@NotNull Icon icon) {
    return IconUtil.toSize(icon,
                           (int)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth(),
                           (int)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getHeight());
  }
}