package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.ExistingTemplatesComponent;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * Structural search plugin main class.
 */
public final class StructuralSearchPlugin implements ProjectComponent, JDOMExternalizable {
  private boolean searchInProgress;
  private boolean replaceInProgress;
  private boolean myDialogVisible;
  private final ConfigurationManager myConfigurationManager = new ConfigurationManager();
  private ExistingTemplatesComponent myExistingTemplatesComponent;

  public boolean isSearchInProgress() {
    return searchInProgress;
  }

  public void setSearchInProgress(boolean searchInProgress) {
    this.searchInProgress = searchInProgress;
  }

  public boolean isReplaceInProgress() {
    return replaceInProgress;
  }

  public void setReplaceInProgress(boolean replaceInProgress) {
    this.replaceInProgress = replaceInProgress;
  }

  public boolean isDialogVisible() {
    return myDialogVisible;
  }

  public void setDialogVisible(boolean dialogVisible) {
    myDialogVisible = dialogVisible;
  }

  /**
   * Method is called after plugin is already created and configured. Plugin can start to communicate with
   * other plugins only in this method.
   */
  public void initComponent() {
  }

  /**
   * This method is called on plugin disposal.
   */
  public void disposeComponent() {
  }

  /**
   * Returns the name of component
   *
   * @return String representing component name. Use PluginName.ComponentName notation
   *  to avoid conflicts.
   */
  @NotNull
  public String getComponentName() {
    return "StructuralSearchPlugin";
  }

  // Simple logging facility

  // Logs given string to IDEA logger

  private static class LoggerHolder {
    private static final Logger logger = Logger.getInstance("Structural search");
  }

  public static void debug(String str) {
    LoggerHolder.logger.info(str);
  }

  public void readExternal(Element element) {
    myConfigurationManager.loadConfigurations(element);
  }

  public void writeExternal(Element element) {
    myConfigurationManager.saveConfigurations(element);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public static StructuralSearchPlugin getInstance(Project project) {
    return project.getComponent(StructuralSearchPlugin.class);
  }

  public ConfigurationManager getConfigurationManager() {
    return myConfigurationManager;
  }

  public ExistingTemplatesComponent getExistingTemplatesComponent() {
    return myExistingTemplatesComponent;
  }

  public void setExistingTemplatesComponent(ExistingTemplatesComponent existingTemplatesComponent) {
    myExistingTemplatesComponent = existingTemplatesComponent;
  }
}
