package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.net.IOExceptionDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 24, 2006
 * Time: 2:47:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class ActionInstallPlugin extends AnAction
{
  final private static String installTitle = IdeBundle.message("action.download.and.install.plugin");
  final private static String updateMessage = IdeBundle.message("action.update.plugin");

  private PluginTable pluginTable;
  private PluginManagerMain host;

  public ActionInstallPlugin( PluginManagerMain mgr, PluginTable table )
  {
    super( installTitle, installTitle, IconLoader.getIcon("/actions/install.png") );

    pluginTable = table;
    host = mgr;
  }

  public void update(AnActionEvent e)
  {
    Presentation presentation = e.getPresentation();
    IdeaPluginDescriptor[] selection = pluginTable.getSelectedObjects();
    boolean enabled = (selection != null);

    if( enabled )
    {
      for( IdeaPluginDescriptor descr : selection )
      {
        if( descr instanceof PluginNode )
        {
          int status = PluginManagerColumnInfo.getRealNodeState((PluginNode) descr);
          enabled = enabled && (status != PluginNode.STATUS_DOWNLOADED);
        }
        else
        if( descr instanceof IdeaPluginDescriptorImpl )
        {
          presentation.setText( updateMessage );
          presentation.setDescription( updateMessage );
          PluginId id = descr.getPluginId();
          enabled = enabled && PluginsTableModel.hasNewerVersion( id );
        }
      }
    }

    presentation.setEnabled( enabled );
  }

  public void actionPerformed(AnActionEvent e)
  {
    IdeaPluginDescriptor[] selection = pluginTable.getSelectedObjects();

    if( userConfirm( selection ) )
    {
      ArrayList<PluginNode> list = new ArrayList<PluginNode>();
      for( IdeaPluginDescriptor descr : selection )
      {
        PluginNode pluginNode = null;
        if (descr instanceof PluginNode)
        {
          pluginNode = (PluginNode)descr;
        }
        else
        if (descr instanceof IdeaPluginDescriptorImpl)
        {
          pluginNode = new PluginNode( descr.getPluginId() );
          pluginNode.setName( descr.getName() );
          pluginNode.setDepends(Arrays.asList( descr.getDependentPluginIds()) );
          pluginNode.setSize( "-1" );
        }

        if( pluginNode != null )
          list.add( pluginNode );
      }
      try
      {
        if( PluginManagerMain.downloadPlugins( list ) )
        {
          host.setRequireShutdown( true );
        }
      }
      catch (IOException e1)
      {
        PluginManagerMain.LOG.error(e1);
        IOExceptionDialog.showErrorDialog(e1, installTitle, IdeBundle.message("error.plugin.download.failed"));
      }
      pluginTable.updateUI();
    }
  }

  //---------------------------------------------------------------------------
  //  Show confirmation message depending on the amount and type of the
  //  selected plugin descriptors: already downloaded plugins need "update"
  //  while non-installed yet need "install".
  //---------------------------------------------------------------------------
  private boolean userConfirm( IdeaPluginDescriptor[] selection )
  {
    String message;
    if( selection.length == 1 )
    {
      if( selection[ 0 ] instanceof IdeaPluginDescriptorImpl )
        message = IdeBundle.message( "prompt.update.plugin", selection[ 0 ].getName() );
      else
        message = IdeBundle.message( "prompt.download.and.install.plugin", selection[ 0 ].getName() );
    }
    else
      message = IdeBundle.message( "prompt.install.several.plugins", selection.length );

    return Messages.showYesNoDialog( host.getMainPanel(), message, installTitle, Messages.getQuestionIcon()) == 0;
  }
}
