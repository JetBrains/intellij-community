package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 26, 2003
 * Time: 9:30:44 PM
 * To change this template use Options | File Templates.
 */
public class PluginManagerConfigurable extends BaseConfigurable implements SearchableConfigurable {

  public boolean EXPANDED = false;
  public String FIND = "";
  public boolean TREE_VIEW = false;

  private PluginManagerMain myPluginManagerMain;
  private PluginManagerUISettings myUISettings;

  public static PluginManagerConfigurable getInstance() {
    return ShowSettingsUtil.getInstance().findApplicationConfigurable(PluginManagerConfigurable.class);
  }

  public PluginManagerConfigurable(final PluginManagerUISettings UISettings) {
    myUISettings = UISettings;
  }

  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  public void reset() {
    myPluginManagerMain.reset();
    myUISettings.getSplitterProportionsData().restoreSplitterProportions(myPluginManagerMain.getMainPanel());
    myUISettings.getAvailableTableProportions().restoreProportion(myPluginManagerMain.getAvailablePluginsTable());
    myUISettings.getInstalledTableProportions().restoreProportion(myPluginManagerMain.getInstalledPluginTable());
  }

  public String getHelpTopic() {
    return "preferences.pluginManager";
  }

  public void disposeUIResources() {
    if (myPluginManagerMain != null) {
      myUISettings.getSplitterProportionsData().saveSplitterProportions(myPluginManagerMain.getMainPanel());
      myUISettings.getAvailableTableProportions().saveProportion(myPluginManagerMain.getAvailablePluginsTable());
      myUISettings.getInstalledTableProportions().saveProportion(myPluginManagerMain.getInstalledPluginTable());
      myPluginManagerMain = null;
    }
  }

  public JComponent createComponent() {
    if (myPluginManagerMain == null) {
      myPluginManagerMain = new PluginManagerMain( new MyInstalledProvider() , new MyAvailableProvider());
    }

    return myPluginManagerMain.getMainPanel();
  }

  public void apply() throws ConfigurationException {
    myPluginManagerMain.apply();
    if (myPluginManagerMain.isRequireShutdown()) {
      if (Messages.showYesNoDialog(IdeBundle.message("message.idea.shutdown.required", ApplicationNamesInfo.getInstance().getProductName()),
                                   IdeBundle.message("title.plugins"), Messages.getQuestionIcon()) == 0) {
        ApplicationManagerEx.getApplicationEx().exit(true);
      }
      else {
        myPluginManagerMain.ignoreChanges ();
      }
    }
  }

  public boolean isModified() {
    return myPluginManagerMain != null && myPluginManagerMain.isModified();
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/pluginManager.png");
  }

  private class MyInstalledProvider implements SortableProvider {
    public int getSortOrder() {
      return myUISettings.INSTALLED_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return myUISettings.INSTALLED_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      myUISettings.INSTALLED_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      myUISettings.INSTALLED_SORT_COLUMN = sortColumn;
    }
  }

  private class MyAvailableProvider implements SortableProvider {
    public int getSortOrder() {
      return myUISettings.AVAILABLE_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return myUISettings.AVAILABLE_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      myUISettings.AVAILABLE_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      myUISettings.AVAILABLE_SORT_COLUMN = sortColumn;
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return new Runnable(){
      public void run() {
        myPluginManagerMain.filter(option);
      }
    };
  }

  public void select(IdeaPluginDescriptor... descriptors) {
    myPluginManagerMain.select(descriptors);
  }
}
