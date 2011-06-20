package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author peter
 */
public interface MvcIcons {
  String ICONS_PATH = "/icons/mvc/";

  Icon CONTROLLER = IconLoader.getIcon(ICONS_PATH + "controller.png");
  Icon SERVICE = IconLoader.getIcon(ICONS_PATH + "service.png");
  Icon DOMAIN_CLASS = IconLoader.getIcon(ICONS_PATH + "domain_class.png");
  Icon CONFIG_FOLDER = IconLoader.getIcon(ICONS_PATH + "config_folder_closed.png");
  Icon ACTION_NODE = IconLoader.getIcon(ICONS_PATH + "action_method.png");
  Icon CONTROLLERS_FOLDER =  IconLoader.getIcon("/nodes/keymapTools.png");
  Icon DOMAIN_CLASSES_FOLDER = IconLoader.getIcon(ICONS_PATH + "modelsNode.png");
  Icon PLUGINS_REFRESH = IconLoader.getIcon(ICONS_PATH + "refresh.png");
  Icon PLUGIN = IconLoader.getIcon(ICONS_PATH + "groovy_mvc_plugin.png");
}
