package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: lloix
 * Date: May 24, 2006
 * Time: 3:19:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class ActionUninstallPlugin extends AnAction
{
  final private static String title = IdeBundle.message("action.uninstall.plugin");
  final private static String promptTitle = IdeBundle.message("title.plugin.uninstall");

  private PluginTable pluginTable;
  private PluginManagerMain host;

  public ActionUninstallPlugin( PluginManagerMain mgr, PluginTable table )
  {
    super( title, title, IconLoader.getIcon("/actions/uninstall.png") );

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
      for( IdeaPluginDescriptor descriptor : selection )
      {
        enabled = enabled && (descriptor instanceof IdeaPluginDescriptorImpl) &&
                             !((IdeaPluginDescriptorImpl)descriptor).isDeleted();
      }
    }
    presentation.setEnabled( enabled );
  }

  public void actionPerformed(AnActionEvent e)
  {
    String  message;
    IdeaPluginDescriptor[] selection = pluginTable.getSelectedObjects();

    if( selection.length == 1 )
      message = IdeBundle.message( "prompt.uninstall.plugin", selection[ 0 ].getName() );
    else
      message = IdeBundle.message( "prompt.uninstall.several.plugins", selection.length );
    if( Messages.showYesNoDialog( host.getMainPanel(), message, promptTitle, Messages.getQuestionIcon()) != 0 )
      return;

    for( IdeaPluginDescriptor descriptor : selection )
    {
      IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl) descriptor;

      boolean actualDelete = true;

      //  Get the list of plugins which depend on this one. If this list is
      //  not empty - issue warning instead of simple prompt.
      ArrayList<IdeaPluginDescriptorImpl> dependant = host.getDependentList( pluginDescriptor );
      if( dependant.size() > 0 )
      {
        message = MessageFormat.format(IdeBundle.message("several.plugins.depend.on.0.continue.to.remove"), pluginDescriptor.getName());
        actualDelete = (Messages.showYesNoDialog( host.getMainPanel(), message, promptTitle, Messages.getQuestionIcon()) == 0 );
      }

      if( actualDelete )
        uninstallPlugin( pluginDescriptor );
    }
  }

  private void uninstallPlugin( IdeaPluginDescriptorImpl descriptor )
  {
    PluginId pluginId = descriptor.getPluginId();
    descriptor.setDeleted( true );

    try
    {
      PluginInstaller.prepareToUninstall( pluginId );
      host.setRequireShutdown( true );
      pluginTable.updateUI();
    }
    catch (IOException e1) {
      PluginManagerMain.LOG.error(e1);
    }
  }
}
