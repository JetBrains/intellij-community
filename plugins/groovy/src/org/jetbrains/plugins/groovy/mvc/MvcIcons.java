package org.jetbrains.plugins.groovy.mvc;

import com.intellij.icons.AllIcons;
import icons.JetgroovyIcons;

import javax.swing.*;

/**
 * @author peter
 */
public interface MvcIcons {
  String ICONS_PATH = "/icons/mvc/";

  Icon CONTROLLER = JetgroovyIcons.Mvc.Controller;
  Icon SERVICE = JetgroovyIcons.Mvc.Service;
  Icon DOMAIN_CLASS = JetgroovyIcons.Mvc.Domain_class;
  Icon CONFIG_FOLDER = JetgroovyIcons.Mvc.Config_folder_closed;
  Icon ACTION_NODE = JetgroovyIcons.Mvc.Action_method;
  Icon CONTROLLERS_FOLDER = AllIcons.Nodes.KeymapTools;
  Icon DOMAIN_CLASSES_FOLDER = JetgroovyIcons.Mvc.ModelsNode;
  Icon PLUGINS_REFRESH = JetgroovyIcons.Mvc.Refresh;
  Icon PLUGIN = JetgroovyIcons.Mvc.Groovy_mvc_plugin;
}
