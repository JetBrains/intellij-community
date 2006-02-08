package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TableUtil;
import com.intellij.util.concurrency.SwingWorker;
import com.intellij.util.net.HTTPProxySettingsDialog;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
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
public class PluginManagerMain
{
  private static Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerMain");

  @NonNls public static final String TEXT_PREFIX = "<html><body style=\"font-family: Arial; font-size: 12pt;\">";
  @NonNls public static final String TEXT_SUFIX = "</body></html>";

  @NonNls public static final String HTML_PREFIX = "<html><body><a href=\"\">";
  @NonNls public static final String HTML_SUFIX = "</a></body></html>";

  public static final String NOT_SPECIFIED = IdeBundle.message("plugin.status.not.specified");

  private JPanel myToolbarPanel;
  private JPanel main;
  private JScrollPane installedScrollPane;
  private JTextPane myDescriptionTextArea;
  private JEditorPane myChangeNotesTextArea;
  private JLabel myVendorLabel;
  private JLabel myVendorEmailLabel;
  private JLabel myVendorUrlLabel;
  private JLabel myPluginUrlLabel;
  private JLabel myVersionLabel;
  private JLabel mySizeLabel;
  private JButton myHttpProxySettingsButton;
  private JProgressBar myProgressBar;
  private JButton btnCancel;
  private JLabel mySynchStatus;

  // actions
  private AnAction installPluginAction;
  private AnAction uninstallPluginAction;

  private PluginTable<IdeaPluginDescriptor> installedPluginTable;

  private ActionToolbar toolbar;
  private CategoryNode root;

  private boolean requireShutdown = false;

  private DefaultActionGroup actionGroup;
  private final SortableProvider myInstalledProvider;
  private PluginsTableModel genericModel;

  public PluginManagerMain(SortableProvider installedProvider )
  {
    myInstalledProvider = installedProvider;

    myDescriptionTextArea.addHyperlinkListener(new MyHyperlinkListener());
    myChangeNotesTextArea.addHyperlinkListener(new MyHyperlinkListener());

    genericModel = new PluginsTableModel(myInstalledProvider);
    installedPluginTable = new PluginTable<IdeaPluginDescriptor>( genericModel );

    installedScrollPane.getViewport().setBackground(installedPluginTable.getBackground());
    installedScrollPane.getViewport().setView(installedPluginTable);
    installedPluginTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e)
        {
          pluginInfoUpdate(installedPluginTable.getSelectedObject());
          toolbar.updateActionsImmediately();
        }
    });
    PopupHandler.installUnknownPopupHandler(installedPluginTable, getActionGroup(), ActionManager.getInstance());

    myToolbarPanel.setLayout(new BorderLayout());
    toolbar = ActionManagerEx.getInstance().createActionToolbar("PluginManaer", getActionGroup(), true);
    myToolbarPanel.add( toolbar.getComponent(), BorderLayout.WEST );
    toolbar.updateActionsImmediately();

    myHttpProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
      }
    });

    myVendorEmailLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorEmailLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e)
      {
          IdeaPluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if( pluginDescriptor != null )
          {
              LaunchStringAction( pluginDescriptor.getVendorEmail(), "mailto:" );
          }
      }
      });

    myVendorUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e)
      {
          IdeaPluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if( pluginDescriptor != null )
          {
              LaunchStringAction( pluginDescriptor.getVendorEmail(), "" );
          }
      }
    });

    myPluginUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myPluginUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e)
      {
          IdeaPluginDescriptor pluginDescriptor = installedPluginTable.getSelectedObject();
          if( pluginDescriptor != null )
          {
              LaunchStringAction( pluginDescriptor.getVendorEmail(), "" );
          }
      }
    });

    new MySpeedSearchBar( installedPluginTable );

    //  Due to the problem that SwingWorker must invoke "finished" in the
    //  appropriate modality state (and at this point modality state differs
    //  from that in which dialog will be updated) we have to wait until
    //  the components (any) is shown - this is the guarantee that modality
    //  state is set to the needed one.
    installedPluginTable.addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) > 0 && installedPluginTable.isShowing()) {
          loadAvailablePlugins(true);
        }
      }
    });
  }

    private void loadAvailablePlugins( boolean checkLocal )
    {
      try
      {
          CategoryNode tempRoot = loadPluginList( checkLocal );
          if( tempRoot == null )
          {
              Messages.showErrorDialog(getMainPanel(), IdeBundle.message("error.list.of.plugins.was.not.loaded"),
                                                       IdeBundle.message("title.plugins"));
              return;
          }
          ModifyPluginsList( tempRoot );
      }
      catch( Exception ex )
      {
        Messages.showErrorDialog(getMainPanel(), IdeBundle.message("error.available.plugins.list.is.not.loaded"),
                                 CommonBundle.getErrorTitle());
      }
    }

  private void  ModifyPluginsList( CategoryNode tempRoot )
  {
    if (root == null) {
      genericModel.AddData(tempRoot);
    }
    else {
      genericModel.ModifyData(tempRoot);
    }

    root = tempRoot;
  }

    private CategoryNode loadPluginList( boolean checkLocal ) throws Exception
    {
      //  If we already have a file with downloaded plugins from the last time,
      //  then read it, load into the list and start the updating process.
      //  Otherwise just start the process of loading the list and save it
      //  into the persistent config file for later reading.
      CategoryNode newRoot = null;
      File file = new File( PathManager.getPluginsPath(), RepositoryHelper.extPluginsFile );
      if( file.exists() && checkLocal )
      {
        newRoot = LoadPluginsFromFile( file );
      }
      LoadPluginsFromHostInBackground();

      return newRoot;
    }

    private static CategoryNode LoadPluginsFromFile( final File file ) throws Exception
    {
      RepositoryContentHandler handler = new RepositoryContentHandler();
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.parse( file, handler );
      return handler.getRoot();
    }

    private static CategoryNode LoadPluginsFromHostFore()
    {
      final StatusProcess statusProcess = new StatusProcess();
        do {
            boolean canceled = ProgressManager.getInstance()
              .runProcessWithProgressSynchronously(statusProcess, IdeBundle.message("progress.downloading.list.of.plugins"), true, null);
            if (canceled && statusProcess.getException() != null)
            {
              if (statusProcess.getException() instanceof IOException) {
                if (! IOExceptionDialog.showErrorDialog((IOException)statusProcess.getException(),
                                                        IdeBundle.message("title.plugin.manager"),
                                                        IdeBundle.message("error.could.not.download.list.of.plugins"))) {
                  break;
                }
              }
              else {
                throw new RuntimeException(statusProcess.getException());
              }
            }
            else{
              break;
            }
            statusProcess.removeOldException();
        }
        while (true);
        return statusProcess.getRoot();
    }

  private void  LoadPluginsFromHostInBackground()
  {
    SetDownloadStatus( true );

    final SwingWorker worker = new SwingWorker() {
      CategoryNode root;
      public Object construct()
      {
        root = StatusProcess.Process( mySynchStatus );
        return root;
      }

      public void finished() {
        StopLoadingPlugins(root);
      }
    };
    worker.start();
  }

  private void  StopLoadingPlugins( CategoryNode root )
  {
    mySynchStatus.setText("Synching data...");
    ModifyPluginsList( root );
    SetDownloadStatus( false );
  }

  private void  SetDownloadStatus( boolean what)
  {
    btnCancel.setVisible( what );
    mySynchStatus.setVisible( what );
    myProgressBar.setVisible( what );
    myProgressBar.setEnabled( what );
    myProgressBar.setIndeterminate( what );
  }

    private ActionGroup getActionGroup () {
      if (actionGroup == null)
      {
        actionGroup = new DefaultActionGroup();

        final String installTitle = IdeBundle.message("action.download.and.install.plugin");
        final String updateMessage = IdeBundle.message("action.update.plugin");
        installPluginAction = new AnAction( installTitle, installTitle, IconLoader.getIcon("/actions/install.png"))
        {
          public void update(AnActionEvent e)
          {
              Presentation presentation = e.getPresentation();
              boolean enabled = false;
              Object pluginObject = installedPluginTable.getSelectedObject();

              if (pluginObject instanceof PluginNode)
              {
                int status = PluginManagerColumnInfo.getRealNodeState((PluginNode)pluginObject);
                enabled = (status != PluginNode.STATUS_DOWNLOADED);
                presentation.setText( installTitle );
              }
              else
              if (pluginObject instanceof IdeaPluginDescriptorImpl )
              {
                presentation.setText(updateMessage);
                presentation.setDescription(updateMessage);
                enabled = PluginsTableModel.NewVersions2Plugins.containsKey( ((IdeaPluginDescriptorImpl)pluginObject).getPluginId() );
              }

            presentation.setEnabled(enabled);
          }

          public void actionPerformed(AnActionEvent e) {
            do {
              try {
                final Object selectedObject = installedPluginTable.getSelectedObject();
                final boolean isUpdate = (selectedObject instanceof IdeaPluginDescriptorImpl);

                PluginNode pluginNode = null;
                if (selectedObject instanceof PluginNode)
                {
                  pluginNode = (PluginNode)selectedObject;
                }
                else
                if (selectedObject instanceof IdeaPluginDescriptorImpl)
                {
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
                      if (node.getPluginId().equals(pluginDescriptor.getPluginId())){
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
                }

                final String message = isUpdate ? updateMessage : installTitle;
                if (Messages.showYesNoDialog(main,
                                             (isUpdate ? IdeBundle.message("prompt.update.plugin", pluginNode.getName()) :
                                                         IdeBundle.message("prompt.download.and.install.plugin", pluginNode.getName()) ),
                                             message,
                                             Messages.getQuestionIcon()) == 0) {
                  if (downloadPlugin(pluginNode)) {
                    requireShutdown = true;
                    installedPluginTable.updateUI();
                  }
                }
                break;
              }
              catch (IOException e1) {
                if (!IOExceptionDialog.showErrorDialog(e1, installTitle, IdeBundle.message("error.plugin.download.failed"))) {
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
          public void update(AnActionEvent e)
          {
            Presentation presentation = e.getPresentation();
            boolean enabled = false;

            if( installedPluginTable != null )
            {
                Object descr = installedPluginTable.getSelectedObject();
                enabled = (descr instanceof IdeaPluginDescriptorImpl) && !((IdeaPluginDescriptorImpl)descr).isDeleted();
            }
            presentation.setEnabled(enabled);
          }

          public void actionPerformed(AnActionEvent e)
          {
            PluginId pluginId = null;

            IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)installedPluginTable.getSelectedObject();
            if (Messages.showYesNoDialog(main, IdeBundle.message("prompt.uninstall.plugin", pluginDescriptor.getName()),
                                         IdeBundle.message("title.plugin.uninstall"), Messages.getQuestionIcon()) == 0) {
              pluginId = pluginDescriptor.getPluginId();
              pluginDescriptor.setDeleted( true );
            }

            if( pluginId != null )
            {
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

    private static class MyHyperlinkListener implements HyperlinkListener {
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

    private static void checkForUpdate(List<PluginNode> updateList, CategoryNode categoryNode)
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

    private static boolean downloadPlugins (final List <PluginNode> plugins) throws IOException {
      final boolean[] result = new boolean[1];
      try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable () {
          public void run() {
            result[0] = PluginInstaller.prepareToInstall(plugins);
          }
        }, IdeBundle.message("progress.download.plugins"), true, null);
      } catch (RuntimeException e) {
        if (e.getCause() != null && e.getCause() instanceof IOException) {
          throw (IOException)e.getCause();
        }
        else {
          throw e;
        }
      }
      return result[0];
    }

    private static boolean downloadPlugin (final PluginNode pluginNode) throws IOException {
      final boolean[] result = new boolean[1];
      try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable () {
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
        if (e.getCause() != null && e.getCause() instanceof IOException) {
          throw (IOException)e.getCause();
        }
        else {
          throw e;
        }
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
  private static void setTextValue( String val, JEditorPane pane)
  {
      if( val != null )
      {
          pane.setText(TEXT_PREFIX + val.trim() + TEXT_SUFIX);
          pane.setCaretPosition( 0 );
      }
      else
      {
          pane.setText("");
      }
  }

  private static void setTextValue( String val, JLabel label)
  {
      label.setText( (val != null) ? val : "" );
  }

    private static void setHtmlValue( final String val, JLabel label)
    {
        boolean isValid = (val != null && val.trim().length() > 0);
        String setVal = isValid ? HTML_PREFIX + val.trim() + HTML_SUFIX : NOT_SPECIFIED;

        label.setText( setVal );
        label.setCursor( new Cursor( isValid ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR ));
    }

    private void pluginInfoUpdate( Object plugin )
    {
        if( plugin != null )
        {
          IdeaPluginDescriptor pluginDescriptor = (IdeaPluginDescriptor)plugin;

          myVendorLabel.setText(pluginDescriptor.getVendor());
          setTextValue( pluginDescriptor.getDescription(), myDescriptionTextArea );
          setTextValue( pluginDescriptor.getChangeNotes(), myChangeNotesTextArea );
          setHtmlValue( pluginDescriptor.getVendorEmail(), myVendorEmailLabel );
          setHtmlValue( pluginDescriptor.getVendorUrl(), myVendorUrlLabel );
          setHtmlValue( pluginDescriptor.getUrl(), myPluginUrlLabel );
          setTextValue( pluginDescriptor.getVersion(), myVersionLabel );

          boolean isInst = !(plugin instanceof PluginNode);
          String size = isInst ? null : ((PluginNode) plugin).getSize();
          if (size != null) {
            size = PluginManagerColumnInfo.getFormattedSize(size);
          }
          setTextValue( size, mySizeLabel );
        }
    }

    private static void  LaunchStringAction( String cmd, String prefix )
    {
        if( cmd != null && cmd.trim().length() > 0)
        {
          try {
            BrowserUtil.launchBrowser( prefix + cmd.trim());
          }
          catch (IllegalThreadStateException ex) { /* not a problem */ }
        }
    }

  private class MySpeedSearchBar extends SpeedSearchBase<PluginTable<IdeaPluginDescriptor>>
  {
    public MySpeedSearchBar( PluginTable<IdeaPluginDescriptor> cmp ){ super( cmp );  }

    public int getSelectedIndex()                 {   return myComponent.getSelectedRow();    }
    public Object[] getAllElements()              {   return myComponent.getElements();       }
    public String getElementText(Object element)  {  return ((IdeaPluginDescriptor)element).getName();   }

    public void selectElement(Object element, String selectedText)
    {
      for( int i = 0; i < myComponent.getRowCount(); i++ ) {
        if( myComponent.getObjectAt(i).getName().equals(((IdeaPluginDescriptor)element).getName())) {
          myComponent.setRowSelectionInterval(i, i);
          TableUtil.scrollSelectionToVisible(myComponent);
          break;
        }
      }
    }
  }
}
