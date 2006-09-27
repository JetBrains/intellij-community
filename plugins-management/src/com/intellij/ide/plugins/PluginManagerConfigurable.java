package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.*;
import com.intellij.peer.PeerFactory;
import com.intellij.util.ui.SortableColumnModel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 26, 2003
 * Time: 9:30:44 PM
 * To change this template use Options | File Templates.
 */
public class PluginManagerConfigurable extends BaseConfigurable implements JDOMExternalizable, SearchableConfigurable, ApplicationComponent {
  public int AVAILABLE_SORT_COLUMN = 0;
  public int INSTALLED_SORT_COLUMN = 0;
  public int CART_SORT_COLUMN = 0;
  public int AVAILABLE_SORT_COLUMN_ORDER = SortableColumnModel.SORT_ASCENDING;
  public int INSTALLED_SORT_COLUMN_ORDER = SortableColumnModel.SORT_ASCENDING;
  public int CART_SORT_COLUMN_ORDER = SortableColumnModel.SORT_ASCENDING;

  private SplitterProportionsData mySplitterProportionsData = PeerFactory.getInstance().getUIHelper().createSplitterProportionsData();
  private TableColumnsProportionData myAvailableTableProportions = new TableColumnsProportionData();
  private TableColumnsProportionData myInstalledTableProportions = new TableColumnsProportionData();

  public boolean EXPANDED = false;
  public String FIND = "";
  public boolean TREE_VIEW = false;

  private PluginManagerMain myPluginManagerMain;
  @NonNls private static final String INSTALLED = "installed";
  @NonNls private static final String AVAILABLE = "available";

  public static PluginManagerConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(PluginManagerConfigurable.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    mySplitterProportionsData.readExternal(element);
    final Element availableTable = element.getChild(AVAILABLE);
    if (availableTable != null) {
      myAvailableTableProportions.readExternal(availableTable);
    }
    final Element installedTable = element.getChild(INSTALLED);
    if (installedTable != null) {
      myInstalledTableProportions.readExternal(element);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    if (myPluginManagerMain != null) {
      mySplitterProportionsData.writeExternal(element);
      final Element availableTable = new Element(AVAILABLE);
      myAvailableTableProportions.writeExternal(availableTable);
      element.addContent(availableTable);
      final Element installedTable = new Element(INSTALLED);
      myInstalledTableProportions.writeExternal(installedTable);
      element.addContent(installedTable);
    }
  }

  @NotNull
  public String getComponentName() {
    return "PluginManagerConfigurable";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  public void reset() {
    myPluginManagerMain.reset();
    mySplitterProportionsData.restoreSplitterProportions(myPluginManagerMain.getMainPanel());
    myAvailableTableProportions.restoreProportion(myPluginManagerMain.getAvailablePluginsTable());
    myInstalledTableProportions.restoreProportion(myPluginManagerMain.getInstalledPluginTable());
  }

  public String getHelpTopic() {
    return "preferences.pluginManager";
  }

  public void disposeUIResources() {
    if (myPluginManagerMain != null) {
      mySplitterProportionsData.saveSplitterProportions(myPluginManagerMain.getMainPanel());
      myAvailableTableProportions.saveProportion(myPluginManagerMain.getAvailablePluginsTable());
      myInstalledTableProportions.saveProportion(myPluginManagerMain.getInstalledPluginTable());
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
    return myPluginManagerMain != null && myPluginManagerMain.isRequireShutdown();
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/pluginManager.png");
  }

  private class MyInstalledProvider implements SortableProvider {
    public int getSortOrder() {
      return INSTALLED_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return INSTALLED_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      INSTALLED_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      INSTALLED_SORT_COLUMN = sortColumn;
    }
  }

  private class MyAvailableProvider implements SortableProvider {
    public int getSortOrder() {
      return AVAILABLE_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return AVAILABLE_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      AVAILABLE_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      AVAILABLE_SORT_COLUMN = sortColumn;
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  public void select(IdeaPluginDescriptor... descriptors) {
    myPluginManagerMain.select(descriptors);
  }
}
