// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ilyas
 */

@State(name = "GroovyApplicationSettings", storages = @Storage("groovy_config.xml"))
public class GroovyApplicationSettings implements PersistentStateComponent<GroovyApplicationSettings> {

  public boolean INTRODUCE_LOCAL_CREATE_FINALS = false;
  public boolean INTRODUCE_LOCAL_SELECT_DEF = true;
  public boolean FORCE_RETURN = false;
  public Boolean EXTRACT_METHOD_SPECIFY_TYPE = null;
  public String EXTRACT_METHOD_VISIBILITY = null;
  public Boolean CONVERT_PARAM_SPECIFY_MAP_TYPE = null;
  public Boolean CONVERT_PARAM_CREATE_NEW_PARAM = null;

  @Override
  public GroovyApplicationSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull GroovyApplicationSettings groovyApplicationSettings) {
    XmlSerializerUtil.copyBean(groovyApplicationSettings, this);
  }

  public static GroovyApplicationSettings getInstance() {
    return ApplicationManager.getApplication().getService(GroovyApplicationSettings.class);
  }

}
