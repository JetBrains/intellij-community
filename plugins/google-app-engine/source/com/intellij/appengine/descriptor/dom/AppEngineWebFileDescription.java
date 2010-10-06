package com.intellij.appengine.descriptor.dom;

import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class AppEngineWebFileDescription extends DomFileDescription<AppEngineWebApp> {
  public AppEngineWebFileDescription() {
    super(AppEngineWebApp.class, "appengine-web-app");
  }

  @Override
  public boolean isMyFile(@NotNull XmlFile file, @Nullable Module module) {
    return file.getName().equals(AppEngineUtil.APP_ENGINE_WEB_XML_NAME);
  }
}
