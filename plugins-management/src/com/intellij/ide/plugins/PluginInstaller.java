package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 29, 2003
 * Time: 9:15:30 PM
 * To change this template use Options | File Templates.
 */
public class PluginInstaller {
  private static final Object lock = new Object();

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

  public static boolean prepareToInstall(final PluginNode pluginNode, boolean packet, long count, long available) throws IOException {
    // check for dependent plugins at first.
    if (pluginNode.getDepends() != null && pluginNode.getDepends().size() > 0) {
      // prepare plugins list for install

      List <PluginNode> depends = new ArrayList <PluginNode> ();
      for (int i = 0; i < pluginNode.getDepends().size(); i++) {
        PluginId depPluginId = pluginNode.getDepends().get(i);

        if (PluginManager.isPluginInstalled(depPluginId)) {
        //  ignore installed plugins
          continue;
        }

        PluginNode depPlugin = new PluginNode(depPluginId);
        depPlugin.setSize("-1");
        depPlugin.setName(depPluginId.getIdString()); //prevent from exceptions
        depends.add(depPlugin);
      }

      if (depends.size() > 0) { // has something to install prior installing the plugin
        final boolean success = prepareToInstall(depends);
        if (!success) {
          return false;
        }
      }
    }

    synchronized (lock) {
      File oldFile = null;
      if (PluginManager.isPluginInstalled(pluginNode.getPluginId())) {
        //store old plugins file
        oldFile = PluginManager.getPlugin(pluginNode.getPluginId()).getPath();
      }
      // download plugin
      File file;
      String errorMessage = IdeBundle.message("unknown.error");
      try {
        file = RepositoryHelper.downloadPlugin(pluginNode, packet, count, available);
      }
      catch(IOException ex) {
        file = null;
        errorMessage = ex.getMessage();
      }
      if (file == null) {
        final String errorMessage1 = errorMessage;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            Messages.showErrorDialog(IdeBundle.message("error.plugin.was.not.installed", pluginNode.getName(), errorMessage1),
                                     IdeBundle.message("title.failed.to.download"));
          }
        });
        return false;
      }

      if (oldFile != null){
        // add command to delete the 'action script' file
        StartupActionScriptManager.ActionCommand deleteOld = new StartupActionScriptManager.DeleteCommand(oldFile);
        StartupActionScriptManager.addActionCommand(deleteOld);
      }

      //noinspection HardCodedStringLiteral
      if (file.getName().endsWith(".jar")) {
        // add command to copy file to the IDEA/plugins path
        StartupActionScriptManager.ActionCommand copyPlugin = new StartupActionScriptManager.CopyCommand(file,
                                                                                                         new File (PathManager.getPluginsPath() +
                                                                                                                   File.separator + file.getName()));
        StartupActionScriptManager.addActionCommand(copyPlugin);
      } else {
        // add command to unzip file to the IDEA/plugins path
        String unzipPath;
        if (ZipUtil.isZipContainsFolder(file))
          unzipPath = PathManager.getPluginsPath();
        else
          unzipPath = PathManager.getPluginsPath() + File.separator + pluginNode.getName();

        StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(file, new File (unzipPath));
        StartupActionScriptManager.addActionCommand(unzip);
      }

      // add command to remove temp plugin file
      StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(file);
      StartupActionScriptManager.addActionCommand(deleteTemp);

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
    synchronized (lock) {
      if (PluginManager.isPluginInstalled(pluginId)) {
        // add command to delete the 'action script' file
        IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(pluginId);

        StartupActionScriptManager.ActionCommand deleteOld = new StartupActionScriptManager.DeleteCommand(pluginDescriptor.getPath());
        StartupActionScriptManager.addActionCommand(deleteOld);
      }
    }
  }

  public static void initPluginClasses () {
    synchronized(lock) {
      File file = new File (getPluginClassesPath());
      if (file.exists())
        file.delete();
    }
  }

  @NonNls
  private static String getPluginClassesPath() {
    return PathManagerEx.getPluginTempPath() + File.separator + "plugin.classes";
  }

  @SuppressWarnings({"unchecked"})
  public static Map<String, String> loadPluginClasses () throws IOException, ClassNotFoundException {
    synchronized(lock) {
      File file = new File (getPluginClassesPath());
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

  public static void savePluginClasses(Map<String, String> classes) throws IOException {
    synchronized(lock) {
      File file = new File (getPluginClassesPath());

      ObjectOutputStream oos = new ObjectOutputStream (new FileOutputStream(file, false));
      try {
        oos.writeObject(classes);
      }
      finally {
        oos.close();
      }
    }
  }
}
