/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.notification.impl.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationModelListener;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.impl.content.GraphicsConfig;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.NotNullFunction;
import com.intellij.util.Processor;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsListPanel extends JPanel implements NotificationModelListener, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.ui.NotificationsListPanel");
  private static final String REMOVE_KEY = "REMOVE";

  private static final Icon CLOSE_ICON = IconLoader.getIcon("/general/balloonClose.png");

  private Project myProject;
  private final Wrapper myWrapper;
  private JComponent myActiveComponent;

  private final JComponent myEmptyComponent;
  private final JComponent myListComponent;

  public NotificationsListPanel(@Nullable final Project project) {
    setLayout(new BorderLayout());
    myProject = project;

    myEmptyComponent = new JLabel("No new notifications.", JLabel.CENTER);
    myListComponent = ItemsList.create(project, this);

    myWrapper = new Wrapper();
    myWrapper.setContent(getCurrentComponent(project));

    setMinimumSize(new Dimension(350, 300));
    add(myWrapper, BorderLayout.CENTER);
  }

  public void notificationsAdded(@NotNull Notification... notification) {
    switchView(myProject);
  }

  public void notificationsRemoved(@NotNull Notification... notification) {
    switchView(myProject);
  }

  public void notificationsRead(@NotNull Notification... notification) {
    switchView(myProject);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    getManager().addListener(this);
  }

  @Override
  public void removeNotify() {
    getManager().removeListener(this);
    super.removeNotify();
  }

  private JComponent getCurrentComponent(@Nullable final Project project) {
    final boolean empty = getManager().count(project) == 0;
    final JComponent component = empty ? myEmptyComponent : myListComponent;
    if (myActiveComponent == component) return null;

    myActiveComponent = component;
    return myActiveComponent;
  }

  protected void switchView(@Nullable final Project project) {
    final JComponent component = getCurrentComponent(project);
    if (component != null) {
      myWrapper.setContent(component);
      myWrapper.revalidate();
      myWrapper.repaint();
    }
  }

  public void dispose() {
    getManager().markRead();

    myProject = null;
  }

  private static NotificationsManagerImpl getManager() {
    return NotificationsManagerImpl.getNotificationsManagerImpl();
  }

  static Dimension getMinSize() {
    final Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
    size.width *= 0.1d;
    size.height *= 0.1d;
    return size;
  }

  public JComponent getPreferredFocusedComponent() {
    return myWrapper.getTargetComponent();
  }

  public static JBPopup show(@Nullable final Project project, @NotNull final JComponent parent) {
    final NotificationsListPanel panel = new NotificationsListPanel(project);
    final ComponentPopupBuilder builder =
      JBPopupFactory.getInstance().createComponentPopupBuilder(panel, panel.getPreferredFocusedComponent());
    final JBPopup popup = builder.setResizable(true).setMinSize(getMinSize()).setDimensionServiceKey(null, "NotificationsPopup", true)
      .setCancelOnClickOutside(false).setBelongsToGlobalPopupStack(false).setCancelButton(new MinimizeButton("Hide")).setMovable(true)
      .setRequestFocus(true).setTitle("Notifications").createPopup();

    popup.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        Disposer.dispose(panel);
      }
    });

    popup.showInCenterOf(SwingUtilities.getRootPane(parent));
    return popup;
  }

  private static class NotificationsListRenderer extends JComponent implements ListCellRenderer {
    private final JTextPane myText;
    private boolean mySelected;
    private boolean myHasFocus;
    private final JLabel myIconLabel;
    private Processor<Cursor> myProc;
    private boolean myWasRead;
    private JList myList;
    private JTextPane myFakeTextPane;
    private JViewport myFakeViewport;

    private NotificationsListRenderer() {
      setLayout(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

      setOpaque(false);

      myIconLabel = new JLabel();
      myIconLabel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
      myIconLabel.setOpaque(false);

      myText = new JTextPane() {
        @Override
        public void setCursor(Cursor cursor) {
          super.setCursor(cursor);
          onCursorChanged(cursor);
        }
      };

      myFakeTextPane = new JTextPane();
      myText.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
      myFakeTextPane.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
      myText.setOpaque(false);
      if (UIUtil.isUnderNimbusLookAndFeel()) {
        myText.setBackground(new Color(0, 0, 0, 0));
      }

      myText.setEditable(false);
      myFakeTextPane.setEditable(false);
      myText.setEditorKit(UIUtil.getHTMLEditorKit());
      myFakeTextPane.setEditorKit(UIUtil.getHTMLEditorKit());

      myFakeViewport = new JViewport();
      myFakeViewport.setView(myFakeTextPane);

      final Wrapper.North comp = new Wrapper.North(myIconLabel);
      comp.setOpaque(false);
      add(comp, BorderLayout.WEST);
      add(myText, BorderLayout.CENTER);
    }

    public JTextPane getText() {
      return myText;
    }

    public void setCursorHandler(Processor<Cursor> proc) {
      myProc = proc;
    }

    public void resetCursorHandler() {
      myProc = null;
    }

    public void onCursorChanged(Cursor cursor) {
      if (myProc != null) myProc.process(cursor);
    }

    @Override
    public Dimension getPreferredSize() {
      final Container parent = myList.getParent();
      if (parent != null) {
        myFakeTextPane.setText(myText.getText());
        final Dimension size = parent.getSize();
        myFakeViewport.setSize(size);
        final Dimension preferredSize = myFakeTextPane.getPreferredSize();

        final Insets insets = getInsets();
        return new Dimension(Math.min(size.width - 20, preferredSize.width), preferredSize.height + insets.top + insets.bottom);
      }

      return super.getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
      final Graphics2D g2d = (Graphics2D)g;

      final Rectangle bounds = getBounds();
      final Insets insets = getInsets();

      final GraphicsConfig cfg = new GraphicsConfig(g);
      cfg.setAntialiasing(true);

      final Shape shape = new RoundRectangle2D.Double(insets.left, insets.top, bounds.width - 1 - insets.left - insets.right,
                                                      bounds.height - 1 - insets.top - insets.bottom, 6, 6);

      if (mySelected) {
        g2d.setColor(UIUtil.getListSelectionBackground());
        g2d.fillRect(0, 0, bounds.width, bounds.height);
      }

      g2d.setColor(Color.WHITE);
      g2d.fill(shape);


      Color bgColor = getBackground();
      if (myWasRead) {
        bgColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 60);
      }

      g2d.setColor(bgColor);
      g2d.fill(shape);

      g2d.setColor(myHasFocus || mySelected ? getBackground().darker().darker() : myWasRead ? getBackground() : getBackground().darker());
      g2d.draw(shape);
      cfg.restore();

      super.paintComponent(g);
    }

    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      LOG.assertTrue(value instanceof Notification);
      final Notification notification = (Notification)value;

      myList = list;

      mySelected = isSelected;
      myHasFocus = cellHasFocus;

      myText.setText(NotificationsUtil.buildHtml(notification));
      myIconLabel.setIcon(NotificationsUtil.getIcon(notification));
      myWasRead = NotificationsManagerImpl.getNotificationsManagerImpl().wasRead(notification);

      setBackground(NotificationsUtil.getBackground(notification));

      return this;
    }
  }

  private static class NotificationsListModel extends AbstractListModel implements NotificationModelListener, Disposable {
    private final List<Notification> myNotifications = new ArrayList<Notification>();
    private NotificationType myType;
    private Project myProject;
    private final NotNullFunction<Project, Collection<Notification>> myRebuildFunction;
    private boolean myArchive;

    private NotificationsListModel(@Nullable Project project) {
      myProject = project;

      myRebuildFunction = new NotNullFunction<Project, Collection<Notification>>() {
        @NotNull
        public Collection<Notification> fun(Project project) {
          return getManager().getByType(myType, project);
        }
      };

      getManager().addListener(this);
      rebuildList();
    }

    public void dispose() {
      getManager().removeListener(this);
      myProject = null;
    }

    public int getSize() {
      return myNotifications.size();
    }

    public Object getElementAt(final int index) {
      return index < myNotifications.size() ? myNotifications.get(index) : null;
    }

    private void rebuildList() {
      myNotifications.clear();
      myNotifications.addAll(myRebuildFunction.fun(myProject));
      fireContentsChanged(this, 0, myNotifications.size() - 1);
    }

    public void filter(final NotificationType type) {
      myType = type;
      rebuildList();
    }

    public void notificationsAdded(@NotNull Notification... notification) {
      rebuildList();
    }

    public void notificationsRemoved(@NotNull Notification... notification) {
      rebuildList();
    }

    public void notificationsRead(@NotNull Notification... notification) {
      rebuildList();
    }
  }

  private static class ItemsList extends JBList {
    private ItemsList(final NotificationsListModel model) {
      super(model);
      setOpaque(false);

      setCellRenderer(new NotificationsListRenderer());
      getSelectionModel().setSelectionInterval(0, 0);

      getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), REMOVE_KEY);
      getActionMap().put(REMOVE_KEY, new AbstractAction() {
        public void actionPerformed(final ActionEvent e) {
          removeSelected();
        }
      });

      setBackground(UIUtil.getPanelBackgound());

      addMouseMotionListener(new MouseMotionListener() {
        public void mouseMoved(MouseEvent e) {
          processMouse(e, false);
        }

        public void mouseDragged(MouseEvent e) {
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(final MouseEvent e) {
          if (!e.isPopupTrigger()) {
            processMouse(e, true);
          }
        }
      });

      ExpireButton.install(this);
    }

    @Override
    protected boolean shouldInstallItemTooltipExpander() {
      return false;
    }

    private void processMouse(final MouseEvent e, final boolean click) {
      final int index = locationToIndex(e.getPoint());
      if (index > -1) {
        final Object value = getModel().getElementAt(index);
        if (value != null && value instanceof Notification) {
          final Notification notification = (Notification)value;
          final Component renderer = getCellRenderer().getListCellRendererComponent(this, value, index, false, false);
          if (renderer instanceof NotificationsListRenderer) {
            final Rectangle bounds = getCellBounds(index, index);
            renderer.setBounds(bounds);
            renderer.doLayout();

            final JTextPane text = ((NotificationsListRenderer)renderer).getText();

            Processor<Cursor> processor;
            HyperlinkListener listener = null;
            if (click) {
              listener = NotificationsUtil.wrapListener(notification);
              if (listener != null) text.addHyperlinkListener(listener);
            }
            else {
              processor = new Processor<Cursor>() {
                public boolean process(Cursor cursor) {
                  ItemsList.this.setCursor(cursor);
                  return true;
                }
              };

              ((NotificationsListRenderer)renderer).setCursorHandler(processor);
            }

            final Point point = e.getPoint();
            point.translate(-bounds.x, -bounds.y);

            final Rectangle r = text.getBounds();
            point.translate(-r.x, -r.y);

            final MouseEvent newEvent =
              new MouseEvent(text, e.getID(), e.getWhen(), e.getModifiers(), point.x, point.y, e.getClickCount(), e.isPopupTrigger(),
                             e.getButton());

            text.dispatchEvent(newEvent);

            ((NotificationsListRenderer)renderer).resetCursorHandler();
            if (listener != null) {
              text.removeHyperlinkListener(listener);
            }
          }
        }
      }
    }

    @SuppressWarnings({"ConstantConditions"})
    @Override
    public NotificationsListModel getModel() {
      final ListModel listModel = super.getModel();
      return listModel instanceof NotificationsListModel ? (NotificationsListModel)listModel : null;
    }

    public void removeSelected() {
      final ListSelectionModel model = getSelectionModel();
      final NotificationsListModel listModel = getModel();
      if (!model.isSelectionEmpty()) {
        final int min = model.getMinSelectionIndex();
        final int max = model.getMaxSelectionIndex();

        final List<Notification> tbr = new ArrayList<Notification>();
        for (int i = min; i <= max; i++) {
          if (model.isSelectedIndex(i)) {
            final Notification notification = (Notification)listModel.getElementAt(i);
            if (notification != null) {
              tbr.add(notification);
            }
          }
        }

        if (tbr.size() > 0) {
          getManager().remove(tbr.toArray(new Notification[tbr.size()]));

          final int toSelect = Math.min(min, listModel.getSize() - 1);
          model.clearSelection();
          if (toSelect >= 0) {
            model.setSelectionInterval(toSelect, toSelect);
            scrollRectToVisible(getCellBounds(toSelect, toSelect));
          }
        }
      }

      revalidate();
      repaint();
    }

    private static void createFilterButton(final JPanel parent,
                                           final ButtonGroup group,
                                           final String title,
                                           final ActionListener listener,
                                           final NotNullFunction<MyButton, String> titleCallback,
                                           final char mnemonic,
                                           final boolean active) {
      final StickyButton b = new MyButton(title, listener) {
        @Override
        public void updateTitle() {
          setText(titleCallback.fun(this));
        }
      };

      parent.add(b);
      group.add(b);

      b.setFocusable(false);
      b.setSelected(active);
      b.setMnemonic(mnemonic);
    }

    private static void updateButtons(@NotNull final JComponent filterBar) {
      final Component[] components = filterBar.getComponents();
      for (final Component c : components) {
        if (c instanceof MyButton) {
          ((MyButton)c).updateTitle();
        }
      }
    }

    private static JComponent buildFilterBar(final ItemsList list, final Project project) {
      final JPanel box = new JPanel();
      box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, box.getBackground().darker()),
                                                       BorderFactory.createEmptyBorder(3, 3, 3, 3)));
      box.setLayout(new BoxLayout(box, BoxLayout.X_AXIS));

      final ButtonGroup buttonGroup = new ButtonGroup();

      createFilterButton(box, buttonGroup, "All", new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          list.filter(null);
        }
      }, new NotNullFunction<MyButton, String>() {
        @NotNull
        public String fun(MyButton myButton) {
          final int i = count(null, project);
          if (i > 0) {
            return String.format("All (%s)", i);
          }

          return "All";
        }
      }, 'A', true);

      createFilterButton(box, buttonGroup, "Error", new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          list.filter(NotificationType.ERROR);
        }
      }, new NotNullFunction<MyButton, String>() {
        @NotNull
        public String fun(MyButton myButton) {
          final int i = count(NotificationType.ERROR, project);
          myButton.setVisible(i > 0);
          if (i > 0) {
            return String.format("Error (%s)", i);
          }
          else if (myButton.isSelected()) {
            switchToAll(buttonGroup);
          }

          return "Error";
        }
      }, 'E', false);

      createFilterButton(box, buttonGroup, "Warning", new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          list.filter(NotificationType.WARNING);
        }
      }, new NotNullFunction<MyButton, String>() {
        @NotNull
        public String fun(MyButton myButton) {
          final int i = count(NotificationType.WARNING, project);
          myButton.setVisible(i > 0);
          if (i > 0) {
            return String.format("Warning (%s)", i);
          }
          else if (myButton.isSelected()) {
            switchToAll(buttonGroup);
          }

          return "Warning";
        }
      }, 'W', false);

      createFilterButton(box, buttonGroup, "Information", new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          list.filter(NotificationType.INFORMATION);
        }
      }, new NotNullFunction<MyButton, String>() {
        @NotNull
        public String fun(MyButton myButton) {
          final int i = count(NotificationType.INFORMATION, project);
          myButton.setVisible(i > 0);
          if (i > 0) {
            return String.format("Information (%s)", i);
          }
          else if (myButton.isSelected()) {
            switchToAll(buttonGroup);
          }

          return "Information";
        }
      }, 'I', false);

      return box;
    }

    private static void switchToAll(final ButtonGroup buttonGroup) {
      final Enumeration<AbstractButton> enumeration = buttonGroup.getElements();
      while (enumeration.hasMoreElements()) {
        final AbstractButton button = enumeration.nextElement();
        if (button.getText().startsWith("All")) {
          button.doClick();
          return;
        }
      }
    }

    private static int count(@Nullable final NotificationType type, @Nullable final Project project) {
      return NotificationsManagerImpl.getNotificationsManagerImpl().getByType(type, project).size();
    }

    private void filter(@Nullable final NotificationType type) {
      final NotificationsListModel listModel = getModel();
      listModel.filter(type);
      if (listModel.getSize() > 0) setSelectedIndex(0);
    }

    public static JComponent create(final Project project, final Disposable parentDisposable) {
      final NotificationsListModel model = new NotificationsListModel(project);
      Disposer.register(parentDisposable, model);

      // TODO: switch filter if removed all of the notifications from current one!

      final ItemsList list = new ItemsList(model);
      list.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          list.setCellRenderer(new NotificationsListRenderer()); // request cell renderer size invalidation
        }
      });

      final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(list);
      scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

      scrollPane.setBorder(null);
      scrollPane.getViewport().setBackground(UIUtil.getPanelBackgound());

      final JComponent buttonBar = buildFilterBar(list, project);
      model.addListDataListener(new ListDataListener() {
        public void intervalAdded(ListDataEvent e) {
          updateButtons(buttonBar);
        }

        public void intervalRemoved(ListDataEvent e) {
          updateButtons(buttonBar);
        }

        public void contentsChanged(ListDataEvent e) {
          updateButtons(buttonBar);
        }
      });

      final JPanel panel = new JPanel(new BorderLayout()) {
        @Override
        public void requestFocus() {
          updateButtons(buttonBar);
          list.requestFocus();
        }
      };

      panel.add(buttonBar, BorderLayout.NORTH);
      panel.add(scrollPane, BorderLayout.CENTER);

      return panel;
    }
  }

  private static class ExpireButton extends JComponent {
    private int myIndex = -1;
    private BaseButtonBehavior myBehavior;
    private JList myList;

    private ExpireButton(final JList list) {
      myList = list;

      final MouseAdapter adapter = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          showAtPoint(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
          hideCloseButton(e);
        }
      };

      list.addMouseMotionListener(adapter);
      list.addMouseListener(adapter);

      setOpaque(false);
      setToolTipText("Delete (" + KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke("DELETE")) + ")");

      myBehavior = new BaseButtonBehavior(this, TimedDeadzone.NULL) {
        @Override
        protected void execute(final MouseEvent e) {
          expire();

          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              showAtPoint(e);
            }
          });
        }
      };
    }

    private void showAtPoint(MouseEvent e) {
      final Point point = e.getSource() == myList ? e.getPoint() : SwingUtilities.convertPoint((Component)e.getSource(), e.getPoint(), myList);
      final int index = myList.locationToIndex(point);
      if (index > -1) {
        final Object value = myList.getModel().getElementAt(index);
        if (value != null && value instanceof Notification) {
          final Rectangle bounds = myList.getCellBounds(index, index);
          if (myList.getVisibleRect().contains(bounds) && bounds.contains(point)) {
            toggle(index, false);
          }
        }
      }
    }

    private void expire() {
      if (myIndex != -1) {
        final ListModel model = myList.getModel();
        final Object o = model.getElementAt(myIndex);
        if (o instanceof Notification) {
          final Notification notification = (Notification)o;
          if (!notification.isExpired()) {
            notification.expire();
          }
        }
      }

      myIndex = -1;
      setVisible(false);
    }

    public void setIndex(final int index) {
      myIndex = index;
    }

    @Override
    public Dimension getPreferredSize() {
      return getMinimumSize();
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(CLOSE_ICON.getIconWidth() + 2, CLOSE_ICON.getIconHeight() + 2);
    }

    @Override
    public void paint(Graphics g) {
      final Rectangle r = getBounds();
      if (myBehavior.isPressedByMouse()) {
        CLOSE_ICON.paintIcon(this, g, r.width - CLOSE_ICON.getIconWidth(), r.height - CLOSE_ICON.getIconHeight());
      }
      else {
        CLOSE_ICON.paintIcon(this, g, r.width - CLOSE_ICON.getIconWidth() - 2, r.height - CLOSE_ICON.getIconHeight() - 2);
      }
    }

    public static void install(final JList list) {
      final ExpireButton button = new ExpireButton(list);
      button.setVisible(false);
    }

    public void hideCloseButton(final MouseEvent e) {
      if (e != null) {
        final Object source = e.getSource();
        if (source instanceof JComponent) {
          final Container parent = getParent();
          if (parent != null) {
            final Point point = SwingUtilities.convertPoint((Component) source, e.getPoint(), getParent());
            if (!getBounds().contains(point)) {
              toggle(-1, true);
            }
          }
        }
      }
      else {
        toggle(-1, true);
      }
    }

    private void toggle(final int index, final boolean hide) {
      if (hide) {
        setIndex(index);
        setVisible(false);
        return;
      }

      if (getParent() == null) {
        final Window window = SwingUtilities.getWindowAncestor(myList);
        if (!(window instanceof JDialog)) return;
        final JLayeredPane layeredPane = ((JDialog)window).getLayeredPane();
        if (layeredPane == null) return;
        layeredPane.add(this);

        layeredPane.addComponentListener(new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            toggle(-1, true);
          }
        });
      }

      if (getParent() != null) {
        final Dimension preferredSize = getPreferredSize();
        final Rectangle cellBounds = myList.getCellBounds(index, index);
        Point location = new Point(cellBounds.x + cellBounds.width - preferredSize.width, cellBounds.y);
        location = SwingUtilities.convertPoint(myList, location, getParent());
        setIndex(index);
        setBounds(location.x, location.y, preferredSize.width, preferredSize.height);
      }

      setVisible(true);
    }

    public int getIndex() {
      return myIndex;
    }
  }

  private abstract static class MyButton extends StickyButton {
    private MyButton(String text, ActionListener listener) {
      super(text, listener);
    }

    private MyButton(String text) {
      super(text);
    }

    public abstract void updateTitle();
  }
}
