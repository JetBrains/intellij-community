package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.ActionToolbarEx;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TableUtil;
import com.intellij.util.net.HTTPProxySettingsDialog;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.zip.ZipException;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 25, 2003
 * Time: 9:47:59 PM
 * To change this template use Options | File Templates.
 */
public class PluginManagerMain {
  private static Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerMain");

  public static final int INSTALLED_TAB = 0;
  public static final int AVAILABLE_TAB = 1;
  //public static final int CART_TAB = 2;

  @NonNls public static final String TEXT_PREFIX = "<html><body style=\"font-family: Arial; font-size: 12pt;\">";
  @NonNls public static final String TEXT_SUFIX = "</body></html>";

  @NonNls public static final String HTML_PREFIX = "<html><body><a href=\"\">";
  @NonNls public static final String HTML_SUFIX = "</a></body></html>";

  public static final String NOT_SPECIFIED = IdeBundle.message("plugin.status.not.specified");

  public static final String INSTALLED_TAB_NAME = IdeBundle.message("tab.plugins.installed");

  private JPanel myToolbarPanel;
  private JPanel main;
  private JLabel myVendorLabel;
  private JLabel myVendorEmailLabel;
  private JLabel myVendorUrlLabel;
  private JLabel myPluginUrlLabel;
  private JEditorPane myDescriptionTextArea;
  private JEditorPane myChangeNotesTextArea;
  private JTabbedPane tabs;
  private JScrollPane installedScrollPane;
  private JScrollPane availableScrollPane;
  //private JScrollPane cartScrollPane;

  // actions
  //private AnAction groupByCategoryAction;
  //private ExpandAllToolbarAction myExpandAllToolbarAction;
  //private CollapseAllToolbarAction myCollapseAllToolbarAction;
  //private AnAction addPluginToCartAction;
  //private AnAction removePluginFromCartAction;
  private AnAction syncAction;
  private AnAction updatePluginsAction;
  //private AnAction findPluginsAction;
  private AnAction installPluginAction;
  private AnAction uninstallPluginAction;

  private PluginTable<IdeaPluginDescriptor> installedPluginTable;
  private PluginTable<PluginNode> availablePluginTable;
  //private PluginTable<PluginNode> cartTable;

  private ActionToolbarEx toolbar;

  private CategoryNode root;

  private boolean requireShutdown = false;

  private DefaultActionGroup actionGroup;
  private JButton myHttpProxySettingsButton;
  private final SortableProvider myAvailableProvider;
  private final SortableProvider myInstalledProvider;
  private final SortableProvider myCartProvider;

  private void pluginInfoUpdate (Object plugin) {
    if (plugin instanceof IdeaPluginDescriptorImpl) {
      IdeaPluginDescriptor pluginDescriptor = (IdeaPluginDescriptor)plugin;

      myVendorLabel.setText(pluginDescriptor.getVendor());

      if (pluginDescriptor.getDescription() != null) {
        myDescriptionTextArea.setText(TEXT_PREFIX + pluginDescriptor.getDescription().trim() + TEXT_SUFIX);
        myDescriptionTextArea.setCaretPosition(0);
      } else {
        myDescriptionTextArea.setText("");
      }

      if (pluginDescriptor.getChangeNotes() != null) {
        myChangeNotesTextArea.setText(TEXT_PREFIX + pluginDescriptor.getChangeNotes().trim() + TEXT_SUFIX);
        myChangeNotesTextArea.setCaretPosition(0);
      } else {
        myChangeNotesTextArea.setText("");
      }

      final String email = pluginDescriptor.getVendorEmail();
      if (email != null && email.trim().length() > 0) {
        myVendorEmailLabel.setText(HTML_PREFIX + email.trim() + HTML_SUFIX);
        myVendorEmailLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myVendorEmailLabel.setText(NOT_SPECIFIED);
        myVendorEmailLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

      final String url = pluginDescriptor.getVendorUrl();
      if (url != null && url.trim().length() > 0) {
        myVendorUrlLabel.setText(HTML_PREFIX + url.trim() + HTML_SUFIX);
        myVendorUrlLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myVendorUrlLabel.setText(NOT_SPECIFIED);
        myVendorUrlLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

      final String homePage = pluginDescriptor.getUrl();
      if (homePage != null && homePage.trim().length() > 0) {
        myPluginUrlLabel.setText(HTML_PREFIX + homePage + HTML_SUFIX);
        myPluginUrlLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myPluginUrlLabel.setText(NOT_SPECIFIED);
        myPluginUrlLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

    } else if (plugin instanceof PluginNode) {
      PluginNode pluginNode = (PluginNode)plugin;

      myVendorLabel.setText(pluginNode.getVendor());

      if (pluginNode.getDescription() != null) {
        myDescriptionTextArea.setText(TEXT_PREFIX + pluginNode.getDescription().trim() + TEXT_SUFIX);
        myDescriptionTextArea.setCaretPosition(0);
      } else {
        myDescriptionTextArea.setText("");
      }

      if (pluginNode.getChangeNotes() != null) {
        myChangeNotesTextArea.setText(TEXT_PREFIX + pluginNode.getChangeNotes().trim() + TEXT_SUFIX);
        myChangeNotesTextArea.setCaretPosition(0);
      } else {
        myChangeNotesTextArea.setText("");
      }

      final String email = pluginNode.getVendorEmail();
      if (email != null && email.trim().length() > 0) {
        myVendorEmailLabel.setText(HTML_PREFIX + email.trim() + HTML_SUFIX);
        myVendorEmailLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myVendorEmailLabel.setText(NOT_SPECIFIED);
        myVendorEmailLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

      final String url = pluginNode.getVendorUrl();
      if (url != null && url.trim().length() > 0) {
        myVendorUrlLabel.setText(HTML_PREFIX + url.trim() + HTML_SUFIX);
        myVendorUrlLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myVendorUrlLabel.setText(NOT_SPECIFIED);
        myVendorUrlLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

      final String homePage = pluginNode.getUrl();
      if (homePage != null && homePage.trim().length() > 0) {
        myPluginUrlLabel.setText(HTML_PREFIX + homePage + HTML_SUFIX);
        myPluginUrlLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
      }
      else {
        myPluginUrlLabel.setText(NOT_SPECIFIED);
        myPluginUrlLabel.setCursor(new Cursor (Cursor.DEFAULT_CURSOR));
      }

    } else {
      myVendorLabel.setText(NOT_SPECIFIED);
      myVendorEmailLabel.setText(NOT_SPECIFIED);
      myVendorUrlLabel.setText(NOT_SPECIFIED);
      myDescriptionTextArea.setText("");
      myChangeNotesTextArea.setText("");
      myPluginUrlLabel.setText(NOT_SPECIFIED);
    }
  }

  public PluginManagerMain(SortableProvider availableProvider, SortableProvider installedProvider, SortableProvider cartProvider) {
    myAvailableProvider = availableProvider;
    myInstalledProvider = installedProvider;
    myCartProvider = cartProvider;
    myToolbarPanel.setLayout(new BorderLayout());
    toolbar = (ActionToolbarEx)ActionManagerEx.getInstance().createActionToolbar("PluginManaer", getActionGroup(), true);

    myToolbarPanel.add(toolbar.getComponent(), BorderLayout.WEST);

    //noinspection HardCodedStringLiteral
    myDescriptionTextArea.setContentType("text/html");
    myDescriptionTextArea.addHyperlinkListener(new MyHyperlinkListener());

    //noinspection HardCodedStringLiteral
    myChangeNotesTextArea.setContentType("text/html");
    myChangeNotesTextArea.addHyperlinkListener(new MyHyperlinkListener());

    installedPluginTable = new PluginTable<IdeaPluginDescriptor>(new InstalledPluginsTableModel(myInstalledProvider));

    installedScrollPane.getViewport().setBackground(installedPluginTable.getBackground());
    installedScrollPane.getViewport().setView(installedPluginTable);
    installedPluginTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        pluginInfoUpdate(installedPluginTable.getSelectedObject());
        toolbar.updateActions();
      }
    });
    PopupHandler.installUnknownPopupHandler(installedPluginTable, getActionGroup(), ActionManager.getInstance());
    tabs.setTitleAt(INSTALLED_TAB, INSTALLED_TAB_NAME + " (" + installedPluginTable.getRowCount() + ")");

    /*
    cartTable = new PluginTable<PluginNode>(new ShoppingCartTableModel(
    PluginManagerConfigurable.getInstance().getCartSortableProvider()));
    cartScrollPane.getViewport().setBackground(cartTable.getBackground());
    cartScrollPane.getViewport().setView(cartTable);
    cartTable.getSelectionModel().addListSelectionListener(new ListSelectionListener () {
    public void valueChanged(ListSelectionEvent e) {
    pluginInfoUpdate(cartTable.getSelectedObject());
    }
    });
    */

    tabs.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        if (tabs.getSelectedIndex() == INSTALLED_TAB) {
          pluginInfoUpdate(installedPluginTable.getSelectedObject());
        }
        else if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
          pluginInfoUpdate(null);

          // load plugin list, if required
          loadAvailablePlugins();
          if (availablePluginTable != null) {
            pluginInfoUpdate(availablePluginTable.getSelectedObject());
          }

          /*
          } else if (tabs.getSelectedIndex() == CART_TAB) {
          pluginInfoUpdate(null);

          // show cart tab
          pluginInfoUpdate(cartTable.getSelectedObject());
          */
        }
        toolbar.updateActions();
      }
    });

    myHttpProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
      }
    });
    /*
    httpProxySettingsLabel.setCursor(new Cursor (Cursor.HAND_CURSOR));
    httpProxySettingsLabel.addMouseListener(new MouseAdapter () {
      public void doAction(MouseEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
      }
    });
    */

    myVendorEmailLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorEmailLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        String email = null;

        if (tabs.getSelectedIndex() == INSTALLED_TAB) {
          IdeaPluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if (pluginDescriptor != null) {
            email = pluginDescriptor.getVendorEmail();
          }
        }
        else if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
          PluginNode pluginNode = availablePluginTable.getSelectedObject();
          if (pluginNode != null) {
            email = pluginNode.getVendorEmail();
          }
          /*
          } else if (tabs.getSelectedIndex() == CART_TAB) {
          PluginNode pluginNode = cartTable.getSelectedObject();
          if (pluginNode != null)
          email = pluginNode.getVendorEmail();
          */
        }

        if (email != null && email.trim().length() > 0) {
          try {
            BrowserUtil.launchBrowser("mailto:" + email.trim());
          }
          catch (IllegalThreadStateException ex) {
            // not a problem
          }
        }
      }
    });

    myVendorUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        String url = null;

        if (tabs.getSelectedIndex() == INSTALLED_TAB) {
          IdeaPluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if (pluginDescriptor != null) {
            url = pluginDescriptor.getVendorUrl();
          }
        }
        else if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
          PluginNode pluginNode = availablePluginTable.getSelectedObject();
          if (pluginNode != null) {
            url = pluginNode.getVendorUrl();
          }
          /*
          } else if (tabs.getSelectedIndex() == CART_TAB) {
          PluginNode pluginNode = cartTable.getSelectedObject();
          if (pluginNode != null)
          url = pluginNode.getVendorUrl();
          */
        }

        if (url != null && url.trim().length() > 0) {
          BrowserUtil.launchBrowser(url.trim());
        }
      }
    });

    myPluginUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myPluginUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        String url = null;

        if (tabs.getSelectedIndex() == INSTALLED_TAB) {
          IdeaPluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if (pluginDescriptor != null) {
            url = pluginDescriptor.getUrl();
          }
        }
        else if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
          PluginNode pluginNode = availablePluginTable.getSelectedObject();
          if (pluginNode != null) {
            url = pluginNode.getUrl();
          }
          /*
          } else if (tabs.getSelectedIndex() == CART_TAB) {
          PluginNode pluginNode = cartTable.getSelectedObject();
          if (pluginNode != null)
          url = pluginNode.getUrl();
          */
        }

        if (url != null && url.trim().length() > 0) {
          BrowserUtil.launchBrowser(url.trim());
        }
      }
    });

    new SpeedSearchBase<PluginTable<IdeaPluginDescriptor>>(installedPluginTable) {
      public int getSelectedIndex() {
        return installedPluginTable.getSelectedRow();
      }

      public Object[] getAllElements() {
        return installedPluginTable.getElements();
      }

      public String getElementText(Object element) {
        return ((IdeaPluginDescriptor)element).getName();
      }

      public void selectElement(Object element, String selectedText) {
        for (int i = 0; i < installedPluginTable.getRowCount(); i++) {
          if (installedPluginTable.getObjectAt(i).getName().equals(((IdeaPluginDescriptor)element).getName())) {
            installedPluginTable.setRowSelectionInterval(i, i);
            TableUtil.scrollSelectionToVisible(installedPluginTable);
            break;
          }
        }
      }
    };
  }

  private void loadAvailablePlugins() {
    try {
      if (root == null) {
        root = loadPluginList();
        if (root == null) {
          Messages.showErrorDialog(getMainPanel(), IdeBundle.message("error.list.of.plugins.was.not.loaded"),
                                   IdeBundle.message("title.plugins"));
          tabs.setSelectedIndex(INSTALLED_TAB);

          return;
        }

        availablePluginTable = new PluginTable<PluginNode>(
          new AvailablePluginsTableModel(root, myAvailableProvider)
        );
        availablePluginTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
          public void valueChanged(ListSelectionEvent e) {
            pluginInfoUpdate(availablePluginTable.getSelectedObject());
            toolbar.updateActions();
          }
        });
        ActionGroup group = getActionGroup();
        PopupHandler.installUnknownPopupHandler(availablePluginTable, group, ActionManager.getInstance());
        availableScrollPane.getViewport().setBackground(availablePluginTable.getBackground());
        availableScrollPane.getViewport().setView(availablePluginTable);

        tabs.setTitleAt(AVAILABLE_TAB, IdeBundle.message("label.plugins.available.count", availablePluginTable.getRowCount()));

        availablePluginTable.requestFocus();

        new SpeedSearchBase<PluginTable<PluginNode>>(availablePluginTable) {
          public int getSelectedIndex() {
            return availablePluginTable.getSelectedRow();
          }

          public Object[] getAllElements() {
            return availablePluginTable.getElements();
          }

          public String getElementText(Object element) {
            return ((PluginNode)element).getName();
          }

          public void selectElement(Object element, String selectedText) {
            for (int i = 0; i < availablePluginTable.getRowCount(); i++) {
              if (availablePluginTable.getObjectAt(i).getName().equals(((PluginNode)element).getName())) {
                availablePluginTable.setRowSelectionInterval(i, i);
                TableUtil.scrollSelectionToVisible(availablePluginTable);
                break;
              }
            }
          }
        };
      }
    } catch (RuntimeException ex) {
      ex.printStackTrace();
      Messages.showErrorDialog(getMainPanel(), IdeBundle.message("error.available.plugins.list.is.not.loaded"),
                               CommonBundle.getErrorTitle());
    }
  }

  private ActionGroup getActionGroup () {
    if (actionGroup == null) {
      actionGroup = new DefaultActionGroup();

      /*
      groupByCategoryAction = new ToggleAction("Group by category",
      "Group plugins by category",
      IconLoader.getIcon("/_cvs/showAsTree.png")) {
      public boolean isSelected(AnActionEvent e) {
      return PluginManagerConfigurable.getInstance().TREE_VIEW;
      }

      public void setSelected(AnActionEvent e, boolean state) {
      PluginManagerConfigurable.getInstance().TREE_VIEW = state;
      // @todo switch table and tree view
      }
      };
      actionGroup.add(groupByCategoryAction);
      actionGroup.addSeparator();
      */

      //myExpandAllToolbarAction = new ExpandAllToolbarAction(myTreeTable);
      //actionGroup.add(myExpandAllToolbarAction);
      //myCollapseAllToolbarAction = new CollapseAllToolbarAction(myTreeTable);
      //actionGroup.add(myCollapseAllToolbarAction);

      /*
      addPluginToCartAction = new AnAction ("Add Plugin to \"Shopping Cart\"",
      "Add Plugin to \"Shopping Cart\"",
      IconLoader.getIcon("/actions/include.png")) {
      public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      boolean enabled = false;

      if (tabs.getSelectedIndex() == AVAILABLE_TAB && availablePluginTable != null) {
      PluginNode pluginNode = availablePluginTable.getSelectedObject();

      if (pluginNode != null) {
      int status = PluginManagerColumnInfo.getRealNodeState(pluginNode);
      if (status == PluginNode.STATUS_MISSING ||
      status == PluginNode.STATUS_NEWEST ||
      status == PluginNode.STATUS_OUT_OF_DATE ||
      status == PluginNode.STATUS_UNKNOWN) {
      enabled = true;
      }
      }
      }

      presentation.setEnabled(enabled);
      }

      public void actionPerformed(AnActionEvent e) {
      if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
      PluginNode pluginNode = availablePluginTable.getSelectedObject();
      if (pluginNode != null) {
      int state = PluginManagerColumnInfo.getRealNodeState(pluginNode);
      if (state == PluginNode.STATUS_MISSING ||
      state == PluginNode.STATUS_NEWEST ||
      state == PluginNode.STATUS_OUT_OF_DATE ||
      state == PluginNode.STATUS_UNKNOWN) {
      ((ShoppingCartTableModel)cartTable.getModel ()).add(pluginNode);
      tabs.setTitleAt(CART_TAB, "Shopping Cart (" + cartTable.getRowCount() + ")");
      }
      }
      }
      }
      };
      actionGroup.add(addPluginToCartAction);

      removePluginFromCartAction = new AnAction ("Delete Plugin from \"Shopping Cart\"",
      "Delete Plugin from \"Shopping Cart\"",
      IconLoader.getIcon("/actions/exclude.png")) {
      public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      boolean enabled = false;

      if (tabs.getSelectedIndex() == CART_TAB) {
      PluginNode pluginNode = cartTable.getSelectedObject();

      if (pluginNode != null) {
      int status = PluginManagerColumnInfo.getRealNodeState(pluginNode);
      if (status == PluginNode.STATUS_CART) {
      enabled = true;
      }
      }
      }

      presentation.setEnabled(enabled);
      }

      public void actionPerformed(AnActionEvent e) {
      if (tabs.getSelectedIndex() == CART_TAB) {
      PluginNode pluginNode = cartTable.getSelectedObject();
      if (pluginNode != null) {
      int state = PluginManagerColumnInfo.getRealNodeState(pluginNode);
      if (state == PluginNode.STATUS_CART) {
      ((ShoppingCartTableModel)cartTable.getModel ()).remove(pluginNode);
      tabs.setTitleAt(CART_TAB, "Shopping Cart (" + cartTable.getRowCount() + ")");
      }
      }
      }
      }
      };
      actionGroup.add(removePluginFromCartAction);
      actionGroup.addSeparator();
      */

      syncAction = new AnAction(IdeBundle.message("action.synchronize.with.plugin.repository"),
                                IdeBundle.message("action.synchronize.with.plugin.repository"),
                                IconLoader.getIcon("/actions/sync.png")) {
        public void update(AnActionEvent e) {
          Presentation presentation = e.getPresentation();
          boolean enabled = false;

          if (tabs.getSelectedIndex() == AVAILABLE_TAB) {
            enabled = true;
          }

          presentation.setEnabled(enabled);
        }

        public void actionPerformed(AnActionEvent e) {
          root = null;
          pluginInfoUpdate(null);
          loadAvailablePlugins();
        }
      };
      //noinspection HardCodedStringLiteral
      syncAction.registerCustomShortcutSet(
        new CustomShortcutSet (KeymapManager.getInstance().getActiveKeymap().getShortcuts("Synchronize")), main);
      actionGroup.add(syncAction);

      updatePluginsAction = new AnAction(IdeBundle.message("action.update.installed.plugins"),
                                         IdeBundle.message("action.update.installed.plugins"),
                                         IconLoader.getIcon("/actions/refresh.png")) {

        public void actionPerformed(AnActionEvent e) {
          if (availablePluginTable == null) {
            loadAvailablePlugins();
          }

          if (root != null)
            do {
              try {
                List<PluginNode> updateList = new ArrayList<PluginNode>();
                checkForUpdate(updateList, root);

                if (updateList.size() == 0) {
                  Messages.showMessageDialog(main,
                                             IdeBundle.message("message.nothing.to.update"),
                                             IdeBundle.message("title.plugin.manager"), Messages.getInformationIcon());
                  break;
                }
                else {
                  Set<PluginNode> pluginsToUpdate = new HashSet<PluginNode>();
                  final IdeaPluginDescriptor[] installedPlugins = PluginManager.getPlugins();
                  for (PluginNode pluginNode : updateList) {
                    for (IdeaPluginDescriptor descriptor : installedPlugins) {
                      if (descriptor.getPluginId().equals(pluginNode.getId())) {
                        pluginsToUpdate.add(pluginNode);
                      }
                    }
                  }

                  DialogWrapper dlg = new PluginsToUpdateChooser(pluginsToUpdate);
                  dlg.show();
                  if (dlg.isOK()){
                    if (downloadPlugins(new ArrayList<PluginNode>(pluginsToUpdate))) {
                      availablePluginTable.updateUI();

                      requireShutdown = true;
                    }
                  }

                  break;
                }
              }
              catch (IOException e1) {
                if (!IOExceptionDialog.showErrorDialog(e1, IdeBundle.message("title.update.installed.plugins"),
                                                       IdeBundle.message("error.plugins.updating.failed"))) {
                  break;
                }
                else {
                  LOG.error(e1);
                }
              }
            }
            while (true);
        }
      };
      actionGroup.add(updatePluginsAction);

      //findPluginsAction = new AnAction("Find", "Find Plugins", IconLoader.getIcon("/actions/find.png")) {
      //  public void actionPerformed(AnActionEvent e) {
      //    Messages.showMessageDialog(main, "Sorry, not implemented yet.", "Find Plugins", Messages.getWarningIcon());

          /*
          PluginManagerConfigurable.getInstance().FIND = JOptionPane.showInputDialog(
          JOptionPane.getRootFrame(), "Find:",
          PluginManagerConfigurable.getInstance().FIND != null ? PluginManagerConfigurable.getInstance().FIND : "");
          if (PluginManagerConfigurable.getInstance().FIND == null ||
          PluginManagerConfigurable.getInstance().FIND.trim().length() == 0) {
          return;
          }
          else {
          PluginNode pluginNode = find(myRoot,
          myCurrentNode,
          PluginManagerConfigurable.getInstance().FIND.trim());
          if (pluginNode == null) {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
          "Nothing was found for '" + PluginManagerConfigurable.getInstance().FIND +
          "'",
          "Find plugin", JOptionPane.INFORMATION_MESSAGE);
          }
          }
          */
      //  }
      //};
      //findPluginsAction.registerCustomShortcutSet(
      //  new CustomShortcutSet (KeymapManager.getInstance().getActiveKeymap().getShortcuts("Find")), main);
      //actionGroup.add(findPluginsAction);

      final String downloadMessage = IdeBundle.message("action.download.and.install.plugin");
      final String updateMessage = IdeBundle.message("action.update.plugin");
      installPluginAction = new AnAction(downloadMessage,
                                         downloadMessage,
                                         IconLoader.getIcon("/actions/install.png")) {
        public void update(AnActionEvent e) {
          Presentation presentation = e.getPresentation();
          boolean enabled = false;
          PluginTable table = tabs.getSelectedIndex() == AVAILABLE_TAB ? availablePluginTable : installedPluginTable;
          if ((tabs.getSelectedIndex() == AVAILABLE_TAB && availablePluginTable != null) ||
              (tabs.getSelectedIndex() == INSTALLED_TAB && installedPluginTable != null)) {
            Object pluginObject = table.getSelectedObject();

            if (pluginObject instanceof PluginNode) {
              int status = PluginManagerColumnInfo.getRealNodeState((PluginNode)pluginObject);
              if (status == PluginNode.STATUS_MISSING ||
                  status == PluginNode.STATUS_NEWEST ||
                  status == PluginNode.STATUS_OUT_OF_DATE ||
                  status == PluginNode.STATUS_UNKNOWN) {
                enabled = true;
              }
              presentation.setText(downloadMessage);
            } else if (pluginObject instanceof IdeaPluginDescriptorImpl){
              presentation.setText(updateMessage);
              presentation.setDescription(updateMessage);
              enabled = true;
            }
          }

          presentation.setEnabled(enabled);
        }

        public void actionPerformed(AnActionEvent e) {
          do {
            try {
              if (root == null){
                loadAvailablePlugins();
              }
              if (root == null) return;
              final boolean isUpdate = tabs.getSelectedIndex() == INSTALLED_TAB;
              PluginTable pluginTable = isUpdate ? installedPluginTable : availablePluginTable;
              if (pluginTable == null) return;
              final Object selectedObject = pluginTable.getSelectedObject();
              PluginNode pluginNode;
              if (selectedObject instanceof PluginNode){
                pluginNode = (PluginNode)selectedObject;
              } else if (selectedObject instanceof IdeaPluginDescriptorImpl) {
                final IdeaPluginDescriptor pluginDescriptor = (IdeaPluginDescriptor)selectedObject;
                pluginNode = new PluginNode(pluginDescriptor.getPluginId());
                pluginNode.setName(pluginDescriptor.getName());
                pluginNode.setDepends(Arrays.asList(pluginDescriptor.getDependentPluginIds()));
                pluginNode.setSize("-1");
                boolean smthFoundToUpdate = false;
                ArrayList<PluginNode> toUpdate = new ArrayList<PluginNode>();
                try {
                  checkForUpdate(toUpdate, root);
                  for (PluginNode node : toUpdate) {
                    if (node.getId().equals(pluginDescriptor.getPluginId())){
                      smthFoundToUpdate = true;
                    }
                  }
                  if (!smthFoundToUpdate){
                    Messages.showMessageDialog(main, IdeBundle.message("message.nothing.to.update"), IdeBundle.message("title.plugin.manager"), Messages.getInformationIcon());
                    return;
                  }
                }
                catch (IOException e1) {
                  LOG.error(e1);
                }
              } else {
                //can't be
                return;
              }

              final String message = isUpdate ? updateMessage : downloadMessage;
              if (Messages.showYesNoDialog(main,
                                           (isUpdate ? IdeBundle.message("prompt.download.and.install.plugin", pluginNode.getName())
                                           : IdeBundle.message("prompt.update.plugin", pluginNode.getName())),
                                           message,
                                           Messages.getQuestionIcon()) == 0) {
                if (downloadPlugin(pluginNode)) {
                  requireShutdown = true;
                  if (availablePluginTable != null){
                    availablePluginTable.updateUI();
                  }
                }
              }
              break;
            }
            catch (IOException e1) {
              if (!IOExceptionDialog.showErrorDialog(e1, downloadMessage, IdeBundle.message("error.plugin.download.failed"))) {
                break;
              }
              else {
                LOG.error(e1);
              }
            }
          }
          while (true);
        }
      };
      actionGroup.add(installPluginAction);

      uninstallPluginAction = new AnAction(IdeBundle.message("action.uninstall.plugin"),
                                           IdeBundle.message("action.uninstall.plugin"),
                                           IconLoader.getIcon("/actions/uninstall.png")) {
        public void update(AnActionEvent e) {
          Presentation presentation = e.getPresentation();
          boolean enabled = false;

          if (installedPluginTable != null && tabs.getSelectedIndex() == INSTALLED_TAB) {
            IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)installedPluginTable.getSelectedObject();

            if (pluginDescriptor != null && ! pluginDescriptor.isDeleted()) {
              enabled = true;
            }
          }
          presentation.setEnabled(enabled);
        }

        public void actionPerformed(AnActionEvent e) {
          PluginId pluginId = null;

          if (tabs.getSelectedIndex() == INSTALLED_TAB) {
            IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)installedPluginTable.getSelectedObject();
            if (pluginDescriptor != null) {
              if (Messages.showYesNoDialog(main, IdeBundle.message("prompt.uninstall.plugin", pluginDescriptor.getName()),
                                           IdeBundle.message("title.plugin.uninstall"), Messages.getQuestionIcon()) == 0) {
                pluginId = pluginDescriptor.getPluginId();
                pluginDescriptor.setDeleted(true);
              }
            }
          }

          if (pluginId != null) {
            try {
              PluginInstaller.prepareToUninstall(pluginId);

              requireShutdown = true;

              installedPluginTable.updateUI();

              /*
              Messages.showMessageDialog(main,
                                            "Plugin \'" + pluginId +
                                            "\' is uninstalled but still running. You will " +
                                            "need to restart IDEA to deactivate it.",
                                            "Plugin Uninstalled",
                                            Messages.getInformationIcon());
              */
            }
            catch (IOException e1) {
              LOG.equals(e1);
            }
          }
        }
      };
      actionGroup.add(uninstallPluginAction);
    }

    return actionGroup;
  }

  public JPanel getMainPanel() {
    return main;
  }

  private class MyHyperlinkListener implements HyperlinkListener {
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        JEditorPane pane = (JEditorPane)e.getSource();
        if (e instanceof HTMLFrameHyperlinkEvent) {
          HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
          HTMLDocument doc = (HTMLDocument)pane.getDocument();
          doc.processHTMLFrameHyperlinkEvent(evt);
        }
        else {
          BrowserUtil.launchBrowser(e.getURL().toString());
        }

      }
    }
  }

  private CategoryNode loadPluginList() {
    StatusProcess statusProcess = new StatusProcess();
    do {
      boolean canceled = ApplicationManager.getApplication().runProcessWithProgressSynchronously(
        statusProcess, IdeBundle.message("progress.downloading.list.of.plugins"), true, null);
      if (canceled && statusProcess.getException() != null) {
        if (statusProcess.getException() instanceof IOException) {
          if (! IOExceptionDialog.showErrorDialog((IOException)statusProcess.getException(), IdeBundle.message("title.plugin.manager"),
                                                  IdeBundle.message("error.could.not.download.list.of.plugins"))) {
            break;
          }
        } else
          throw new RuntimeException(statusProcess.getException());
      } else {
        break;
      }
      statusProcess.removeOldException();
    }
    while (true);

    return statusProcess.getRoot();
  }

  public Object getSelectedPlugin () {
    switch (tabs.getSelectedIndex()) {
      case INSTALLED_TAB:
        return installedPluginTable.getSelectedObject();
      case AVAILABLE_TAB:
        return availablePluginTable.getSelectedObject();
        /*
        case CART_TAB:
        return cartTable.getSelectedObject();
        */
      default:
        return null;
    }
  }

  private void checkForUpdate(List<PluginNode> updateList, CategoryNode categoryNode)
    throws IOException {
    for (int i = 0; i < categoryNode.getPlugins().size(); i++) {
      PluginNode pluginNode = categoryNode.getPlugins().get(i);
      if (PluginManagerColumnInfo.getRealNodeState(pluginNode) == PluginNode.STATUS_OUT_OF_DATE) {
        updateList.add(pluginNode);
      }
    }

    if (categoryNode.getChildCount() > 0) {
      for (int i = 0; i < categoryNode.getChildren().size(); i++) {
        CategoryNode node = categoryNode.getChildren().get(i);
        checkForUpdate(updateList, node);
      }
    }
  }

  private boolean downloadPlugins (final List <PluginNode> plugins) throws IOException {
    final boolean[] result = new boolean[1];
    try {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(
        new Runnable () {
          public void run() {
            result[0] = PluginInstaller.prepareToInstall(plugins);
          }
        }, IdeBundle.message("progress.download.plugins"), true, null);
    } catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException)
        throw (IOException)e.getCause();
      else
        throw e;
    }
    return result[0];
  }

  private boolean downloadPlugin (final PluginNode pluginNode) throws IOException {
    final boolean[] result = new boolean[1];
    try {
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(
        new Runnable () {
          public void run() {
            ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

            pi.setText(pluginNode.getName());

            try {
              result[0] = PluginInstaller.prepareToInstall(pluginNode);
            } catch (ZipException e) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showErrorDialog(IdeBundle.message("error.plugin.zip.problems", pluginNode.getName()),
                                           IdeBundle.message("title.installing.plugin"));

                }
              });
            } catch (IOException e) {
              throw new RuntimeException (e);
            }
          }
        }, IdeBundle.message("progress.download.plugin", pluginNode.getName()), true, null);
    } catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException)
        throw (IOException)e.getCause();
      else
        throw e;
    }

    return result[0];
  }

  public boolean isRequireShutdown() {
    return requireShutdown;
  }

  public void ignoreChages() {
    requireShutdown = false;
  }


  private class PluginsToUpdateChooser extends DialogWrapper{
    private Set<PluginNode> myPluginsToUpdate;
    private SortedSet<PluginNode> myModel;
    protected PluginsToUpdateChooser(Set<PluginNode> pluginsToUpdate) {
      super(false);
      myPluginsToUpdate = pluginsToUpdate;
      myModel = new TreeSet<PluginNode>(new Comparator<PluginNode>() {
        public int compare(final PluginNode o1, final PluginNode o2) {
          if (o1 == null) return 1;
          if (o2 == null) return -1;
          return o1.getName().compareToIgnoreCase(o2.getName());
        }
      });
      myModel.addAll(myPluginsToUpdate);
      init();
      setTitle(IdeBundle.message("title.choose.plugins.to.update"));
    }

    protected JComponent createCenterPanel() {
      OrderPanel<PluginNode> panel = new OrderPanel<PluginNode>(PluginNode.class){
        public boolean isCheckable(final PluginNode entry) {
          return true;
        }

        public boolean isChecked(final PluginNode entry) {
          return myPluginsToUpdate.contains(entry);
        }

        public void setChecked(final PluginNode entry, final boolean checked) {
          if (checked){
            myPluginsToUpdate.add(entry);
          } else {
            myPluginsToUpdate.remove(entry);
          }
        }
      };
      for (PluginNode pluginNode : myModel) {
        panel.add(pluginNode);
      }
      panel.setCheckboxColumnName("");
      panel.getEntryTable().setTableHeader(null);
      return panel;
    }
  }
}
