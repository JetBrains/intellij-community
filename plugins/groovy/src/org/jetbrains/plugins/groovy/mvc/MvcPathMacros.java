package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.CollectionFactory;

import java.util.Set;

/**
 * @author peter
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UtilityClassWithPublicConstructor"})
public class MvcPathMacros {

  public MvcPathMacros() {
    Set<String> macroNames = PathMacros.getInstance().getUserMacroNames();
    for (String framework : CollectionFactory.ar("grails", "griffon")) {
      String name = "USER_HOME_" + framework.toUpperCase();
      if (!macroNames.contains(name)) { // OK, it may appear/disappear during the application lifetime, but we ignore that for now. Restart will help anyway
        PathMacros.getInstance().addLegacyMacro(name, StringUtil.trimEnd(getSdkWorkDirParent(framework), "/"));
      }
    }
  }

  public static String getSdkWorkDirParent(String framework) {
    String grailsWorkDir = System.getProperty(framework + ".work.dir");
    if (StringUtil.isNotEmpty(grailsWorkDir)) {
      grailsWorkDir = FileUtil.toSystemIndependentName(grailsWorkDir);
      if (!grailsWorkDir.endsWith("/")) {
        grailsWorkDir += "/";
      }
      return grailsWorkDir;
    }

    return StringUtil.trimEnd(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()), "/") + "/." + framework + "/";
  }
}
