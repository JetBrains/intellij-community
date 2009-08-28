package com.intellij.notification.impl.ui;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.Notification;
import com.intellij.notification.impl.NotificationModelListener;
import com.intellij.notification.impl.NotificationsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.ui.popup.util.MinimizeButton;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.NotNullFunction;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * @author spleaner
 */
public class NotificationsListPanel extends JPanel implements NotificationModelListener<Notification> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.ui.NotificationsListPanel");
  private static final String REMOVE_KEY = "REMOVE";

  private WeakReference<JBPopup> myPopupRef;

  private JList myList;
  private NotificationsListModel myDataModel;

  private Wrapper myContentPane = new Wrapper();
  private NotificationComponent myNotificationComponent;
  private Project myProject;
  private JComponent myFilterBar;
  private boolean myArchive;

  public NotificationsListPanel(@NotNull final NotificationComponent component) {
    setLayout(new BorderLayout());

    myNotificationComponent = component;
    myProject = component.getProject();

    myDataModel = new NotificationsListModel(myProject);

    myList = new JList(myDataModel) {
      @Override
      public String getToolTipText(final MouseEvent event) {
        final int i = locationToIndex(event.getPoint());
        if (i >= 0) {
          final Object o = getModel().getElementAt(i);
          if (o instanceof Notification) {
            return ((Notification)o).getDescription();
          }
        }

        return null;
      }
    };

    myList.setBackground(myContentPane.getBackground());
    myList.setCellRenderer(new NotificationsListRenderer());
    myList.getSelectionModel().setSelectionInterval(0, 0);
    myList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() % 2 == 0) {
          final int index = myList.locationToIndex(e.getPoint());
          final Object o = myList.getModel().getElementAt(index);
          if (o instanceof Notification) {
            performNotificationAction((Notification) o);
          }
        }
      }
    });

    myList.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), REMOVE_KEY);
    myList.getActionMap().put(REMOVE_KEY, new AbstractAction() {
      public void actionPerformed(final ActionEvent e) {
        removeSelected();
      }
    });

    final JScrollPane scrollPane = new JScrollPane(myList);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

    final JPanel wrapperPane = new JPanel();
    wrapperPane.setLayout(new BorderLayout());

    wrapperPane.add(scrollPane, BorderLayout.CENTER);

    myFilterBar = buildFilterBar(myDataModel);
    wrapperPane.add(myFilterBar, BorderLayout.NORTH);

    myContentPane.setContent(wrapperPane);
    add(myContentPane, BorderLayout.CENTER);

    updateButtons();
  }

  private static NotificationsManager getManager() {
    return NotificationsManager.getNotificationsManager();
  }

  private JComponent buildFilterBar(@NotNull final NotificationsListModel listModel) {
    final JPanel box = new JPanel();
    box.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
    box.setLayout(new BoxLayout(box, BoxLayout.X_AXIS));

    final ButtonGroup buttonGroup = new ButtonGroup();

    createFilterButton(box, buttonGroup, "All", new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myArchive = false;
        listModel.filter(null);
        resetSelection();
      }
    }, new NotNullFunction<MyButton, String>() {
      @NotNull
      public String fun(MyButton myButton) {
        final int i = getManager().count(myProject);
        if (i > 0) {
          return String.format("All (%s)", i);
        } else {
          return "All";
        }
      }
    }, true);

    createFilterButton(box, buttonGroup, "Error", new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myArchive = false;
        listModel.filter(NotificationType.ERROR);
        resetSelection();
      }
    }, new NotNullFunction<MyButton, String>() {
      @NotNull
      public String fun(MyButton myButton) {
        final int i = getManager().getByType(NotificationType.ERROR, myProject).size();
        if (i > 0) {
          return String.format("Error (%s)", i);
        }

        return "Error";
      }
    }, false);

    createFilterButton(box, buttonGroup, "Warning", new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myArchive = false;
        listModel.filter(NotificationType.WARNING);
        resetSelection();
      }
    }, new NotNullFunction<MyButton, String>() {
      @NotNull
      public String fun(MyButton myButton) {
        final int i = getManager().getByType(NotificationType.WARNING, myProject).size();
        if (i > 0) {
          return String.format("Warning (%s)", i);
        }

        return "Warning";
      }
    }, false);

    createFilterButton(box, buttonGroup, "Information", new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        myArchive = false;
        listModel.filter(NotificationType.INFORMATION);
        resetSelection();
      }
    }, new NotNullFunction<MyButton, String>() {
      @NotNull
      public String fun(MyButton myButton) {
        final int i = getManager().getByType(NotificationType.INFORMATION, myProject).size();
        if (i > 0) {
          return String.format("Information (%s)", i);
        }

        return "Information";
      }
    }, false);

    box.add(Box.createHorizontalGlue());

    createFilterButton(box, buttonGroup, "Archive", new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myArchive = true;
        listModel.showArchive();
        resetSelection();
      }
    }, new NotNullFunction<MyButton, String>() {
      @NotNull
      public String fun(MyButton myButton) {
        final int i = getManager().getArchive(myProject).size();
        if (i > 0) {
          return String.format("Archive (%s)", i);
        }

        return "Archive";
      }
    }, false);

    return box;
  }

  private void resetSelection() {
    if (myDataModel.getSize() > 0) {
      myList.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  private static void createFilterButton(final JPanel parent,
                                         final ButtonGroup group,
                                         final String title,
                                         final ActionListener listener,
                                         final NotNullFunction<MyButton, String> titleCallback,
                                         final boolean active) {
    final StickyButton b = new MyButton(title, listener) {
      @Override
      public void updateTitle() {
        setText(titleCallback.fun(this));
      }
    };

    parent.add(b);
    group.add(b);
    b.setSelected(active);
  }

  public void notificationsAdded(@NotNull final Notification... notification) {
    myDataModel.rebuildList();
    updateButtons();
  }

  public void notificationsRemoved(@NotNull final Notification... notification) {
    myDataModel.rebuildList();
    updateButtons();
  }

  public void notificationsArchived(@NotNull Notification... notification) {
    myDataModel.rebuildList();
    updateButtons();
  }

  private void updateButtons() {
    final Component[] components = myFilterBar.getComponents();
    for (final Component c : components) {
      if (c instanceof MyButton) {
        ((MyButton)c).updateTitle();
      }
    }
  }

  private void performNotificationAction(final Notification notification) {
    final NotificationListener.Continue onClose = notification.getListener().perform();
    if (onClose == NotificationListener.Continue.REMOVE) {
      getManager().remove(notification);
      revalidateAll();
    }
  }

  static Dimension getMinSize() {
    final Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
    size.width *= 0.1d;
    size.height *= 0.1d;
    return size;
  }

  public void clear() {
    if (myPopupRef != null) {
      final JBPopup jbPopup = myPopupRef.get();
      if (jbPopup != null) {
        jbPopup.cancel();
      }

      myPopupRef = null;
    }
  }

  private void revalidateAll() {
    myContentPane.revalidate();
    myContentPane.repaint();
  }

  public void removeSelected() {
    final ListSelectionModel model = myList.getSelectionModel();
    if (!model.isSelectionEmpty()) {
      final int min = model.getMinSelectionIndex();
      final int max = model.getMaxSelectionIndex();

      final List<Notification> tbr = new ArrayList<Notification>();
      for (int i = min; i <= max; i++) {
        if (model.isSelectedIndex(i)) {
          final Notification notification = (Notification)myDataModel.getElementAt(i);
          if (notification != null) {
            final NotificationListener.Continue onRemove = notification.getListener().onRemove();
            if (onRemove == NotificationListener.Continue.REMOVE) {
              tbr.add(notification);
            }
          }
        }
      }

      if (tbr.size() > 0) {
        getManager().remove(tbr.toArray(new Notification[tbr.size()]));

        final int toSelect = Math.min(min, myDataModel.getSize() - 1);
        model.clearSelection();
        if (toSelect >= 0) {
          model.setSelectionInterval(toSelect, toSelect);
          myList.scrollRectToVisible(myList.getCellBounds(toSelect, toSelect));
        }
      }
    }

    revalidateAll();
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  public boolean showOrHide() {
    if (myPopupRef != null) {
      final JBPopup popup = myPopupRef.get();
      if (popup != null) {
        popup.cancel();
      }

      myPopupRef = null;
      return false;
    }

    final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(this, getPreferredFocusedComponent());
    final JBPopup popup = builder.setResizable(true).setMinSize(getMinSize()).setDimensionServiceKey(null, "NotificationsPopup", true)
      .setCancelOnClickOutside(false).setBelongsToGlobalPopupStack(false).setCancelButton(new MinimizeButton("Hide")).setMovable(true)
      .setRequestFocus(true).setTitle("Notifications").createPopup();

    popup.addListener(new JBPopupListener.Adapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        final NotificationsManager manager = getManager();
        manager.removeListener(NotificationsListPanel.this);
        manager.archive();
      }
    });

    getManager().addListener(this);
    myPopupRef = new WeakReference<JBPopup>(popup);
    popup.showInCenterOf(SwingUtilities.getRootPane(myNotificationComponent));
    return true;
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

  private static class NotificationsListRenderer extends JPanel implements ListCellRenderer {
    public static final Border SEPARATION_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(204, 204, 204));
    public static final Border DEFAULT_BORDER = BorderFactory.createEmptyBorder(5, 3, 3, 3);

    private JLabel myTitleLabel;
    private JLabel myTimestampLabel;

    private NotificationsListRenderer() {
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setBorder(BorderFactory.createCompoundBorder(SEPARATION_BORDER, DEFAULT_BORDER));

      myTitleLabel = new JLabel();
      add(myTitleLabel);
      add(Box.createHorizontalGlue());

      myTimestampLabel = new JLabel();
      myTimestampLabel.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN, 10));
      myTimestampLabel.setForeground(Color.GRAY);

      add(myTimestampLabel);
    }

    public Component getListCellRendererComponent(final JList list,
                                                  final Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      LOG.assertTrue(value instanceof Notification);
      final Notification notification = (Notification)value;

      setIcon(notification.getIcon());
      final Color color = list.getSelectionBackground();
      setBackground(isSelected ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 80) : list.getBackground());

      if (cellHasFocus) {
        setBorder(BorderFactory.createCompoundBorder(SEPARATION_BORDER,
                                                     BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, color),
                                                                                        BorderFactory.createEmptyBorder(4, 2, 2, 2))));
      }
      else {
        setBorder(BorderFactory.createCompoundBorder(SEPARATION_BORDER, DEFAULT_BORDER));
      }

      setTitle(notification.getName());
      setTimestamp(DateFormatUtil.formatDate(new Date(), notification.getDate()));
      return this;
    }

    private void setIcon(@NotNull final Icon icon) {
      myTitleLabel.setIcon(icon);
    }

    private void setTitle(@NotNull final String title) {
      myTitleLabel.setText(title);
    }

    private void setTimestamp(@NotNull final String component) {
      myTimestampLabel.setText(component);
    }
  }

  private static class NotificationsListModel extends AbstractListModel {
    private List<Notification> myNotifications = new ArrayList<Notification>();
    private NotificationType myType;
    private Project myProject;
    private NotNullFunction<Project, Collection<Notification>> myRebuildFunction;

    private NotificationsListModel(@Nullable Project project) {
      myProject = project;
      rebuildList();
    }

    private static NotNullFunction<Project, Collection<Notification>> createDefaultRebuildFunction() {
      return new NotNullFunction<Project, Collection<Notification>>() {
        @NotNull
        public Collection<Notification> fun(Project project) {
          return getManager().getByType(null, project);
        }
      };
    }

    public int getSize() {
      return myNotifications.size();
    }

    public Object getElementAt(final int index) {
      return myNotifications.get(index);
    }

    private void rebuildList(NotNullFunction<Project, Collection<Notification>> fun) {
      myRebuildFunction = fun;
      rebuildList();
    }

    private void rebuildList() {
      if (myRebuildFunction == null) {
        myRebuildFunction = createDefaultRebuildFunction();
      }

      myNotifications.clear();
      myNotifications.addAll(myRebuildFunction.fun(myProject));

      fireContentsChanged(this, 0, myNotifications.size() - 1);
    }

    public void filter(final NotificationType type) {
      myType = type;
      rebuildList(new NotNullFunction<Project, Collection<Notification>>() {
        @NotNull
        public Collection<Notification> fun(Project project) {
          return getManager().getByType(type, project);
        }
      });
    }

    public void showArchive() {
      myType = null;
      rebuildList(new NotNullFunction<Project, Collection<Notification>>() {
        @NotNull
        public Collection<Notification> fun(Project project) {
          return getManager().getArchive(myProject);
        }
      });
    }
  }
}
