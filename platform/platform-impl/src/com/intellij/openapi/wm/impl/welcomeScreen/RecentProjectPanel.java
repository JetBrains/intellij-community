// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.icons.AllIcons;
import com.intellij.ide.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.IconUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RecentProjectPanel extends JPanel {
  public static final String RECENT_PROJECTS_LABEL = "Recent Projects";
  protected final JBList myList;
  protected final UniqueNameBuilder<ReopenProjectAction> myPathShortener;
  protected AnAction removeRecentProjectAction;
  private int myHoverIndex = -1;
  private final int closeButtonInset = JBUI.scale(7);
  private Icon currentIcon = toSize(AllIcons.Welcome.Project.Remove);
  private static final Logger LOG = Logger.getInstance(RecentProjectPanel.class);
  Set<ReopenProjectAction> projectsWithLongPathes = new HashSet<>(0);

  private final JPanel myCloseButtonForEditor = new JPanel() {
    {
      setPreferredSize(new Dimension(currentIcon.getIconWidth(), currentIcon.getIconHeight()));
      setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
      currentIcon.paintIcon(this, g, 0, 0);
    }
  };

  protected FilePathChecker myChecker;


  private boolean rectInListCoordinatesContains(Rectangle listCellBounds,  Point p) {

    int realCloseButtonInset = (UIUtil.isJreHiDPI(this)) ?
                               (int)(closeButtonInset * JBUI.sysScale(this)) : closeButtonInset;

    Rectangle closeButtonRect = new Rectangle(myCloseButtonForEditor.getX() - realCloseButtonInset,
                                              myCloseButtonForEditor.getY() - realCloseButtonInset,
                                              myCloseButtonForEditor.getWidth() + realCloseButtonInset * 2,
                                              myCloseButtonForEditor.getHeight() + realCloseButtonInset * 2);

    Rectangle rectInListCoordinates = new Rectangle(new Point(closeButtonRect.x + listCellBounds.x,
                                                              closeButtonRect.y + listCellBounds.y),
                                                    closeButtonRect.getSize());
    return rectInListCoordinates.contains(p);
  }

  public RecentProjectPanel(@NotNull Disposable parentDisposable) {
    super(new BorderLayout());

    final AnAction[] recentProjectActions = RecentProjectsManager.getInstance().getRecentProjectsActions(false, isUseGroups());

    myPathShortener = new UniqueNameBuilder<>(SystemProperties.getUserHome(), File.separator, 40);
    Collection<String> pathsToCheck = ContainerUtil.newHashSet();
    for (AnAction action : recentProjectActions) {
      if (action instanceof ReopenProjectAction) {
        final ReopenProjectAction item = (ReopenProjectAction)action;

        myPathShortener.addPath(item, item.getProjectPath());
        pathsToCheck.add(item.getProjectPath());
      }
    }

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

    myList = createList(recentProjectActions, getPreferredScrollableViewportSize());
    myList.setCellRenderer(createRenderer(myPathShortener));

    new ClickListener(){
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex >= 0) {
          Rectangle cellBounds = myList.getCellBounds(selectedIndex, selectedIndex);
          if (cellBounds.contains(event.getPoint())) {
            Object selection = myList.getSelectedValue();
            if (Registry.is("removable.welcome.screen.projects") && rectInListCoordinatesContains(cellBounds, event.getPoint())) {
              removeRecentProject();
            } else if (selection != null) {
              AnAction selectedAction = (AnAction) selection;
              AnActionEvent actionEvent = AnActionEvent.createFromInputEvent(selectedAction, event, ActionPlaces.WELCOME_SCREEN);
              selectedAction.actionPerformed(actionEvent);

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
        final Object[] selectedValued = myList.getSelectedValues();
        if (selectedValued != null) {
          for (Object selection : selectedValued) {
            AnActionEvent event = AnActionEvent.createFromInputEvent((AnAction)selection, null, ActionPlaces.WELCOME_SCREEN);
            ((AnAction)selection).actionPerformed(event);
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

    JComponent list = recentProjectActions.length == 0
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

  private void removeRecentProject() {
    Object[] selection = myList.getSelectedValues();

    if (selection != null && selection.length > 0) {
      final int rc = Messages.showOkCancelDialog(RecentProjectPanel.this,
                                                 "Remove '" + StringUtil
                                                   .join(selection, action -> ((AnAction)action).getTemplatePresentation().getText(), "'\n'") +
                                                 "' from recent projects list?",
                                                 "Remove Recent Project",
                                                 Messages.getQuestionIcon());
      if (rc == Messages.OK) {
        for (Object projectAction : selection) {
          removeRecentProjectElement(projectAction);
        }
        ListUtil.removeSelectedItems(myList);
      }
    }
  }

  protected boolean isPathValid(String path) {
    return myChecker == null || myChecker.isValid(path);
  }

  protected static void removeRecentProjectElement(Object element) {
    final RecentProjectsManager manager = RecentProjectsManager.getInstance();
    if (element instanceof ReopenProjectAction) {
      manager.removePath(((ReopenProjectAction)element).getProjectPath());
    }
    else if (element instanceof ProjectGroupActionGroup) {
      final ProjectGroup group = ((ProjectGroupActionGroup)element).getGroup();
      for (String path : group.getProjects()) {
        manager.removePath(path);
      }
      manager.removeGroup(group);
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
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
            IdeFocusManager.getGlobalInstance().requestFocus(myList, true);
          });
        }
        if (myList.getSelectedIndices().length > 1) {
          return;
        }
        if (myIsEngaged && !UIUtil.isSelectionButtonDown(e) && !(focusOwner instanceof JRootPane)) {
          Point point = e.getPoint();
          int index = myList.locationToIndex(point);
          myList.setSelectedIndex(index);

          final Rectangle cellBounds = myList.getCellBounds(index, index);
          if (cellBounds != null && cellBounds.contains(point)) {
            UIUtil.setCursor(myList, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (rectInListCoordinatesContains(cellBounds, point)) {
              currentIcon = toSize(AllIcons.Welcome.Project.Remove_hover);
            } else {
              currentIcon = toSize(AllIcons.Welcome.Project.Remove);
            }
            myHoverIndex = index;
            myList.repaint(cellBounds);
          }
          else {
            UIUtil.setCursor(myList, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            myHoverIndex = -1;
            myList.repaint();
          }
        }
        else {
          myIsEngaged = true;
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myHoverIndex = -1;
        currentIcon = toSize(AllIcons.Welcome.Project.Remove);
        myList.repaint();
      }
    };

    myList.addMouseMotionListener(mouseAdapter);
    myList.addMouseListener(mouseAdapter);

  }

  protected JBList createList(AnAction[] recentProjectActions, Dimension size) {
    return new MyList(size, recentProjectActions);
  }

  protected ListCellRenderer createRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
    return new RecentProjectItemRenderer(pathShortener);
  }

  @Nullable
  protected JPanel createTitle() {
    JPanel title = new JPanel() {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, JBUI.scale(28));
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

    private MyList(Dimension size, @NotNull AnAction[] listData) {
      super(listData);
      mySize = size;
      setExpandableItemsEnabled(false);
      setEmptyText("  No Project Open Yet  ");
      setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      getAccessibleContext().setAccessibleName(RECENT_PROJECTS_LABEL);
      final MouseHandler handler = new MouseHandler();
      addMouseListener(handler);
      addMouseMotionListener(handler);
    }

    public Rectangle getCloseIconRect(int index) {
      final Rectangle bounds = getCellBounds(index, index);
      Icon icon = toSize(AllIcons.Welcome.Project.Remove);
      return new Rectangle(bounds.width - icon.getIconWidth() - JBUI.scale(10),
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
      final int i = locationToIndex(event.getPoint());
      if (i != -1) {
        final Object elem = getModel().getElementAt(i);
        if (elem instanceof ReopenProjectAction) {
          @SystemIndependent String path = ((ReopenProjectAction)elem).getProjectPath();
          boolean valid = isPathValid(path);
          if (!valid || RecentProjectPanel.this.projectsWithLongPathes.contains(elem)) {
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
        myMousePoint = e.getPoint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myMousePoint = null;
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        myMousePoint = e.getPoint();
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        final Point point = e.getPoint();
        final MyList list = MyList.this;
        final int index = list.locationToIndex(point);
        if (index != -1) {
          if (getCloseIconRect(index).contains(point)) {
            e.consume();
            final Object element = getModel().getElementAt(index);
            removeRecentProjectElement(element);
            ListUtil.removeSelectedItems(MyList.this);
          }
        }
      }
    }
  }

  protected class RecentProjectItemRenderer extends JPanel implements ListCellRenderer {

    protected final JLabel myName = new JLabel();
    protected final JLabel myPath = new JLabel();
    protected boolean myHovered;
    protected JPanel myCloseThisItem = myCloseButtonForEditor;

    private final UniqueNameBuilder<ReopenProjectAction> myShortener;

    protected RecentProjectItemRenderer(UniqueNameBuilder<ReopenProjectAction> pathShortener) {
      super(new VerticalFlowLayout());
      myShortener = pathShortener;
      myPath.setFont(JBUI.Fonts.label(SystemInfo.isMac ? 10f : 11f));
      setFocusable(true);
      layoutComponents();
    }

    protected void layoutComponents() {
      add(myName);
      add(myPath);
    }

    protected Color getListBackground(boolean isSelected, boolean hasFocus) {
      return UIUtil.getListBackground(isSelected);
    }

    protected Color getListForeground(boolean isSelected, boolean hasFocus) {
      return UIUtil.getListForeground(isSelected);
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      myHovered = myHoverIndex == index;
      Color fore = getListForeground(isSelected, list.hasFocus());
      Color back = getListBackground(isSelected, list.hasFocus());

      myName.setForeground(fore);
      myPath.setForeground(isSelected ? fore : UIUtil.getInactiveTextColor());

      setBackground(back);

      if (value instanceof ReopenProjectAction) {
        ReopenProjectAction item = (ReopenProjectAction)value;
        myName.setText(item.getTemplatePresentation().getText());
        myPath.setText(getTitle2Text(item, myPath, JBUI.scale(40)));
      } else if (value instanceof ProjectGroupActionGroup) {
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
        int maxWidth = RecentProjectPanel.this.getWidth() - leftOffset - (int)ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.getWidth() - JBUI.scale(10);
        if (maxWidth > 0 && fm.stringWidth(fullText) > maxWidth) {
          int left = 1; int right = 1;
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
      } catch (Exception e) {
        LOG.error("Path label font: " + pathLabel.getFont());
        LOG.error("Panel width: " + RecentProjectPanel.this.getWidth());
        LOG.error(e);
      }

      return fullText;
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      return new Dimension(Math.min(size.width, JBUI.scale(245)), size.height);
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
    public void delayedApplicationDeactivated(@NotNull IdeFrame ideFrame) {
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
          pathIsValid = isFileAvailable(new File(path));
        }
        catch (Exception e) {
          pathIsValid = false;
        }

        if (myInvalidPaths.contains(path) == pathIsValid) {
          if (pathIsValid) {
            myInvalidPaths.remove(path);
          } else {
            myInvalidPaths.add(path);
          }
          ApplicationManager.getApplication().invokeLater(myCallback);
        }
        scheduleCheck(path, Math.max(MIN_AUTO_UPDATE_MILLIS, 10 * (System.currentTimeMillis() - startTime)));
      }, delay, TimeUnit.MILLISECONDS);
    }
  }

  private static boolean isFileAvailable(File file) {
    List<File> roots = Arrays.asList(File.listRoots());
    File tmp = file;
    while(tmp != null) {
      if (roots.contains(tmp)) {
        return file.exists();
      }
      tmp = tmp.getParentFile();
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
