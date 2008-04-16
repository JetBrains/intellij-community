/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;

import java.util.ArrayList;

/**
 * @author ilyas
 */

@State(
    name = "GroovyApplicationSettings",
    storages = {
    @Storage(
        id = "groovy_config",
        file = "$APP_CONFIG$/groovy_config.xml"
    )}
)
public class GroovyApplicationSettings implements PersistentStateComponent<GroovyApplicationSettings> {

  public Boolean SPECIFY_TYPE_EXPLICITLY = null;
  public Boolean INTRODUCE_LOCAL_CREATE_FINALS = null;
  public Boolean EXTRACT_METHOD_SPECIFY_TYPE = null;
  public String EXTRACT_METHOD_VISIBILITY = null;
  public Boolean IS_DEBUG_ENABLED_IN_SCRIPT = null;


  // Groovy configuration settings
  public String DEFAULT_GROOVY_VERSION = null;
  public ArrayList<String> GROOVY_VERSIONS = new ArrayList<String>();
  public String GROOVY_INSTALL_PATH = null;

  @Transient
  private boolean myJsSupportEnabled = false;

  public GroovyApplicationSettings getState() {
    return this;
  }

  public void loadState(GroovyApplicationSettings groovyApplicationSettings) {
    XmlSerializerUtil.copyBean(groovyApplicationSettings, this);
  }

  public static GroovyApplicationSettings getInstance() {
    return ServiceManager.getService(GroovyApplicationSettings.class);
  }

  public boolean isJsSupportEnabled() {
    return myJsSupportEnabled;
  }

  public void setJsSupportEnabled(final boolean jsSupportEnabled) {
    myJsSupportEnabled = jsSupportEnabled;
  }
}
