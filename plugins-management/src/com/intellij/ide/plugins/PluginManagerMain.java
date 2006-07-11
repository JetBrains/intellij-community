package com.intellij.ide.plugins;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.OrderPanel;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SpeedSearchBase;
import com.intellij.ui.TableUtil;
import com.intellij.util.concurrency.SwingWorker;
import com.intellij.util.net.HTTPProxySettingsDialog;
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
  public static Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerMain");

  @NonNls public static final String TEXT_PREFIX = "<html><body style=\"font-family: Arial; font-size: 12pt;\">";
  @NonNls public static final String TEXT_SUFIX = "</body></html>";

  @NonNls public static final String HTML_PREFIX = "<html><body><a href=\"\">";
  @NonNls public static final String HTML_SUFIX = "</a></body></html>";

  private boolean requireShutdown = false;

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

  private PluginTable pluginTable;
  private ArrayList<IdeaPluginDescriptor> pluginsList;

  private ActionToolbar toolbar;
  private DefaultActionGroup actionGroup;
  private PluginsTableModel genericModel;

  public PluginManagerMain(SortableProvider installedProvider )
  {
    myDescriptionTextArea.addHyperlinkListener(new MyHyperlinkListener());
    myChangeNotesTextArea.addHyperlinkListener(new MyHyperlinkListener());

    genericModel = new PluginsTableModel(installedProvider);
    pluginTable = new PluginTable( genericModel );

    installedScrollPane.getViewport().setBackground(pluginTable.getBackground());
    installedScrollPane.getViewport().setView(pluginTable);

    pluginTable.getSelectionModel().addListSelectionListener(
      new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e)
        {
          pluginInfoUpdate(pluginTable.getSelectedObject());
          toolbar.updateActionsImmediately();
        }
    });

    //  Add handler for right mouse button - select a row for which
    //  we want to show a context menu.
    pluginTable.addMouseListener( new MouseAdapter()
    {
       public void mousePressed( MouseEvent e)
       {
         Point p = e.getPoint();
         int row = pluginTable.rowAtPoint( p );
         if( row != -1 && SwingUtilities.isRightMouseButton( e )) {
           pluginTable.setRowSelectionInterval(row, row);
         }
       }
    });
    PopupHandler.installUnknownPopupHandler(pluginTable, getActionGroup(), ActionManager.getInstance());

    myToolbarPanel.setLayout(new BorderLayout());
    toolbar = ActionManager.getInstance().createActionToolbar("PluginManaer", getActionGroup(), true);
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
          IdeaPluginDescriptor pluginDescriptor = pluginTable.getSelectedObject();
          if( pluginDescriptor != null )
          {
            //noinspection HardCodedStringLiteral
            LaunchStringAction( pluginDescriptor.getVendorEmail(), "mailto:" );
          }
      }
      });

    myVendorUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e)
      {
          IdeaPluginDescriptor pluginDescriptor = pluginTable.getSelectedObject();
          if( pluginDescriptor != null )
          {
              LaunchStringAction( pluginDescriptor.getVendorUrl(), "" );
          }
      }
    });

    myPluginUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myPluginUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e)
      {
          IdeaPluginDescriptor pluginDescriptor = pluginTable.getSelectedObject();
          if( pluginDescriptor != null )
          {
              LaunchStringAction( pluginDescriptor.getUrl(), "" );
          }
      }
    });

    new MySpeedSearchBar( pluginTable );

    //  Due to the problem that SwingWorker must invoke "finished" in the
    //  appropriate modality state (and at this point modality state differs
    //  from that in which dialog will be updated) we have to wait until
    //  the components (any) is shown - this is the guarantee that modality
    //  state is set to the needed one.
    pluginTable.addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) > 0 && pluginTable.isShowing()) {
          loadAvailablePlugins(true);
        }
      }
    });
  }

  public void setRequireShutdown( boolean val )
  {
    requireShutdown = val;
  }

  public ArrayList<IdeaPluginDescriptorImpl> getDependentList( IdeaPluginDescriptorImpl pluginDescriptor )
  {
    return genericModel.dependent( pluginDescriptor );
  }

  private void loadAvailablePlugins( boolean checkLocal )
  {
    ArrayList<IdeaPluginDescriptor> list;
    try
    {
      //  If we already have a file with downloaded plugins from the last time,
      //  then read it, load into the list and start the updating process.
      //  Otherwise just start the process of loading the list and save it
      //  into the persistent config file for later reading.
      File file = new File( PathManager.getPluginsPath(), RepositoryHelper.extPluginsFile );
      if( file.exists() && checkLocal )
      {
        RepositoryContentHandler handler = new RepositoryContentHandler();
        SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
        parser.parse( file, handler );
        list = handler.getPluginsList();
        modifyPluginsList( list );
      }
    }
    catch( Exception ex )
    {
      //  Nothing to do, just ignore - if nothing can be read from the local
      //  file just start downloading of plugins' list from the site.
    }
    loadPluginsFromHostInBackground();
  }

  private void modifyPluginsList(ArrayList<IdeaPluginDescriptor> list) {
    IdeaPluginDescriptor[] selected = pluginTable.getSelectedObjects();
    if (pluginsList == null) {
      genericModel.addData(list);
    }
    else {
      genericModel.modifyData(list);
    }
    pluginsList = list;
    if (selected != null) {
      select(selected);
    }
  }

  /**
   * Start a new thread which downloads new list of plugins from the site in
   * the background and updates a list of plugins in the table.
   */
  private void loadPluginsFromHostInBackground()
  {
    setDownloadStatus( true );

    new SwingWorker() {
      ArrayList<IdeaPluginDescriptor> list = null;
      Exception error;

      public Object construct() {
        try { list = RepositoryHelper.Process( mySynchStatus ); }
        catch( Exception e ) { error = e; }
        return list;
      }

      public void finished() {
        if (list != null) {
          modifyPluginsList( list );
        }
        else if( error != null )
          Messages.showErrorDialog( IdeBundle.message("error.list.of.plugins.was.not.loaded"), IdeBundle.message("title.plugins"));
        setDownloadStatus( false );
      }
    }.start();
  }

  private void setDownloadStatus( boolean what)
  {
    btnCancel.setVisible( what );
    mySynchStatus.setVisible( what );
    myProgressBar.setVisible( what );
    myProgressBar.setEnabled( what );
    myProgressBar.setIndeterminate( what );
  }

  private ActionGroup getActionGroup ()
  {
    if( actionGroup == null )
    {
      actionGroup = new DefaultActionGroup();
      actionGroup.add( new ActionInstallPlugin( this, pluginTable ) );
      actionGroup.add( new ActionUninstallPlugin( this, pluginTable ) );
    }
    return actionGroup;
  }

  public JPanel getMainPanel() {
    return main;
  }

  public static boolean downloadPlugins (final List <PluginNode> plugins) throws IOException {
    final boolean[] result = new boolean[ 1 ];
    try {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable () {
        public void run() {
          result[ 0 ] = PluginInstaller.prepareToInstall( plugins );
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

  public static boolean downloadPlugin (final PluginNode pluginNode) throws IOException
  {
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

  public void ignoreChanges() {
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
      String setVal = isValid ? HTML_PREFIX + val.trim() + HTML_SUFIX : IdeBundle.message("plugin.status.not.specified");

      label.setText( setVal );
      label.setCursor( isValid ? Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) :
                                 Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
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

  private static class MySpeedSearchBar extends SpeedSearchBase<PluginTable>
  {
    public MySpeedSearchBar( PluginTable cmp ){ super( cmp );  }

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

  public void select(IdeaPluginDescriptor... descriptors) {
    pluginTable.select(descriptors);
  }
}
