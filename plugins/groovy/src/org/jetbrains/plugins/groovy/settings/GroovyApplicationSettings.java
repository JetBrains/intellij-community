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
import org.jetbrains.annotations.NonNls;

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

  public boolean SPECIFY_VAR_TYPE_EXPLICITLY = false;
  public boolean INTRODUCE_LOCAL_CREATE_FINALS = false;
  public Boolean EXTRACT_METHOD_SPECIFY_TYPE = null;
  public String EXTRACT_METHOD_VISIBILITY = null;
  public Boolean CONVERT_PARAM_SPECIFY_MAP_TYPE = null;
  public Boolean CONVERT_PARAM_CREATE_NEW_PARAM = null;

  // Groovy & Grails configuration settings
  public String DEFAULT_GROOVY_LIB_NAME = null;
  public String DEFAULT_GRAILS_LIB_NAME = null;

  @Transient private final boolean myJsSupportEnabled = classExists("com.intellij.lang.javascript.psi.JSElement");
  @Transient private final boolean myCssSupportEnabled = classExists("com.intellij.psi.css.CssElement");

  private boolean classExists(@NonNls String qname) {
    try {
      Class.forName(qname);
      return true;
    }
    catch (ClassNotFoundException e) {
      return false;
    }
  }

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

  public boolean isCssSupportEnabled() {
    return myCssSupportEnabled;
  }
}
