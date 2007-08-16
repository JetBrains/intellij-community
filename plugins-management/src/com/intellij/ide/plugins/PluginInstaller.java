package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 29, 2003
 * Time: 9:15:30 PM
 * To change this template use Options | File Templates.
 */
public class PluginInstaller {

  private PluginInstaller() {}

  public static boolean prepareToInstall (List <PluginNode> plugins) {
    ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

    long total = 0;
    for (PluginNode pluginNode : plugins) {
      total += Long.valueOf(pluginNode.getSize()).longValue();
    }

    long count = 0;
    boolean result = false;

    for (PluginNode pluginNode : plugins) {
      pi.setText(pluginNode.getName());

      try {
        result |= prepareToInstall(pluginNode, true, count, total);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      count += Integer.valueOf(pluginNode.getSize()).intValue();
    }
    return result;
  }

  private static boolean prepareToInstall(final PluginNode pluginNode, boolean packet, long count, long available) throws IOException {
    // check for dependent plugins at first.
    if (pluginNode.getDepends() != null && pluginNode.getDepends().size() > 0) {
      // prepare plugins list for install

      final PluginId[] optionalDependentPluginIds = pluginNode.getOptionalDependentPluginIds();
      final List <PluginNode> depends = new ArrayList <PluginNode> ();
      final List<PluginNode> optionalDeps = new ArrayList<PluginNode>();
      for (int i = 0; i < pluginNode.getDepends().size(); i++) {
        PluginId depPluginId = pluginNode.getDepends().get(i);

        if (PluginManager.isPluginInstalled(depPluginId)) {
        //  ignore installed plugins
          continue;
        }

        PluginNode depPlugin = new PluginNode(depPluginId);
        depPlugin.setSize("-1");
        depPlugin.setName(depPluginId.getIdString()); //prevent from exceptions

        if (optionalDependentPluginIds != null && Arrays.binarySearch(optionalDependentPluginIds, depPluginId) != -1) {
          optionalDeps.add(depPlugin);
        } else {
          depends.add(depPlugin);
        }
      }

      if (depends.size() > 0) { // has something to install prior installing the plugin
        final boolean [] proceed = new boolean[1];
        final StringBuffer buf = new StringBuffer();
        for (PluginNode depend : depends) {
          buf.append(depend.getName()).append(",");
        }
        try {
          SwingUtilities.invokeAndWait(new Runnable(){
            public void run() {
              proceed[0] = Messages.showYesNoDialog(IdeBundle.message("plugin.manager.dependencies.detected.message", depends.size(), buf.substring(0, buf.length() - 1)),
                                                    IdeBundle.message("plugin.manager.dependencies.detected.title"),
                                                    Messages.getWarningIcon())
                           == DialogWrapper.OK_EXIT_CODE;
            }
          });
        }
        catch (Exception e) {
          return false;
        }
        if (proceed[0]) {
          if (!prepareToInstall(depends)) {
            return false;
          }
        } else {
          return false;
        }
      }

      if (optionalDeps.size() > 0) {
        final StringBuffer buf = new StringBuffer();
        for (PluginNode depend : optionalDeps) {
          buf.append(depend.getName()).append(",");
        }
        final boolean [] proceed = new boolean[1];
        try {
          SwingUtilities.invokeAndWait(new Runnable(){
            public void run() {
              proceed[0] = Messages.showYesNoDialog(IdeBundle.message("plugin.manager.optional.dependencies.detected.message", optionalDeps.size(), buf.substring(0, buf.length() - 1)),
                                                    IdeBundle.message("plugin.manager.dependencies.detected.title"), 
                                                    Messages.getWarningIcon())
                           == DialogWrapper.OK_EXIT_CODE;
            }
          });
        }
        catch (Exception e) {
          return false;
        }
        if (proceed[0]) {
          if (!prepareToInstall(optionalDeps)) {
            return false;
          }
        }
      }
    }

    synchronized (PluginManager.lock) {
      final String buildNumber = RepositoryHelper.ExtractBuildNumber();
      final @NonNls String url = RepositoryHelper.DOWNLOAD_URL +
                         URLEncoder.encode(pluginNode.getPluginId().getIdString(), "UTF8") +
                         "&build=" + buildNumber;
      new PluginDownloader(pluginNode.getPluginId().getIdString(), url, null, null, pluginNode.getName())
        .prepareToInstall(ProgressManager.getInstance().getProgressIndicator());

      pluginNode.setStatus(PluginNode.STATUS_DOWNLOADED);
    }

    return true;
  }
  /**
   * Install plugin into a temp direcotry
   * Append 'action script' file with installing actions
   *
   * @param pluginNode Plugin to install
   */
  public static boolean prepareToInstall (PluginNode pluginNode) throws IOException {
    return prepareToInstall(pluginNode, false, 0, 0);
  }

  public static void prepareToUninstall (PluginId pluginId) throws IOException {
    synchronized (PluginManager.lock) {
      if (PluginManager.isPluginInstalled(pluginId)) {
        // add command to delete the 'action script' file
        IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(pluginId);

        StartupActionScriptManager.ActionCommand deleteOld = new StartupActionScriptManager.DeleteCommand(pluginDescriptor.getPath());
        StartupActionScriptManager.addActionCommand(deleteOld);
      }
    }
  }

  @SuppressWarnings({"unchecked"})
  public static Map<String, String> loadPluginClasses () throws IOException, ClassNotFoundException {
    synchronized(PluginManager.lock) {
      File file = new File (PluginManager.getPluginClassesPath());
      if (file.exists()) {
        ObjectInputStream ois = new ObjectInputStream (new FileInputStream (file));
        try {
          return (Map<String, String>)ois.readObject();
        }
        finally {
          ois.close();
        }
      } else {
        return new HashMap<String, String> ();
      }
    }
  }

}
