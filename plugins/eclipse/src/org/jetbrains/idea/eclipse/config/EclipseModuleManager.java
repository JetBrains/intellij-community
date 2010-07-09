/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
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

package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.components.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;

@State(
  name = "EclipseModuleManager",
  storages = {
    @Storage(
      id = "default",
      file = "$MODULE_FILE$"
    )
  }
)
public class EclipseModuleManager implements PersistentStateComponent<Element>{
  @NonNls private static final String VALUE_ATTR = "value";
  @NonNls private static final String VARELEMENT = "varelement";
  @NonNls private static final String VAR_ATTRIBUTE = "var";
  @NonNls private static final String CONELEMENT = "conelement";
  @NonNls private static final String FORCED_JDK = "forced_jdk";
  private CachedXmlDocumentSet myDocumentSet;
  private final Map<String, String> myEclipseVariablePaths = new LinkedHashMap<String, String>();
  private final Set<String> myEclipseUrls = new LinkedHashSet<String>();
  private final Set<String> myUnknownCons = new LinkedHashSet<String>();
  private boolean myForceConfigureJDK = false;
  private static final String SRC_PREFIX = "src:";
  private static final String SRC_LINK_PREFIX = "linksrc:";
  private final Module myModule;
  @NonNls private static final String LIBELEMENT = "libelement";

  public EclipseModuleManager(Module module) {
    myModule = module;
  }

  public static EclipseModuleManager getInstance(Module module) {
    return ModuleServiceManager.getService(module, EclipseModuleManager.class);
  }

  public CachedXmlDocumentSet getDocumentSet() {
    return myDocumentSet;
  }

  public void setDocumentSet(final CachedXmlDocumentSet documentSet) {
    myDocumentSet = documentSet;
  }

  public void registerEclipseVariablePath(String path, String var) {
    myEclipseVariablePaths.put(path, var);
  }

  public void registerEclipseSrcVariablePath(String path, String var) {
    myEclipseVariablePaths.put(SRC_PREFIX + path, var);
  }

  public void registerEclipseLinkedSrcVarPath(String path, String var) {
    myEclipseVariablePaths.put(SRC_LINK_PREFIX + path, var);
  }

  public String getEclipseLinkedSrcVariablePath(String path) {
    return myEclipseVariablePaths.get(SRC_LINK_PREFIX + path);
  }

  public String getEclipseVariablePath(String path) {
    return myEclipseVariablePaths.get(path);
  }

  public String getEclipseSrcVariablePath(String path) {
    return myEclipseVariablePaths.get(SRC_PREFIX + path);
  }

  public void registerUnknownCons(String con) {
    myUnknownCons.add(con);
  }

  public Set<String> getUnknownCons() {
    return myUnknownCons;
  }

  public boolean isForceConfigureJDK() {
    return myForceConfigureJDK;
  }

  public void setForceConfigureJDK() {
    myForceConfigureJDK = true;
  }

  public void registerEclipseLibUrl(String url) {
    myEclipseUrls.add(url);
  }

  public boolean isEclipseLibUrl(String url) {
    return myEclipseUrls.contains(url);
  }

  public Element getState() {
    if (ClasspathStorage.getStorageType(myModule) != EclipseClasspathStorageProvider.ID) {
      if (!myEclipseUrls.isEmpty() || !myEclipseVariablePaths.isEmpty() || myForceConfigureJDK || !myUnknownCons.isEmpty()) {
        Element root = new Element("EclipseModuleSettings");
        for (String eclipseUrl : myEclipseUrls) {
          final Element libElement = new Element(LIBELEMENT);
          libElement.setAttribute(VALUE_ATTR, eclipseUrl);
          root.addContent(libElement);
        }
        for (String var : myEclipseVariablePaths.keySet()) {
          Element varElement = new Element(VARELEMENT);
          varElement.setAttribute(VAR_ATTRIBUTE, var);
          varElement.setAttribute(VALUE_ATTR, myEclipseVariablePaths.get(var));
          root.addContent(varElement);
        }
        for (String unknownCon : myUnknownCons) {
          Element conElement = new Element(CONELEMENT);
          conElement.setAttribute(VALUE_ATTR, unknownCon);
          root.addContent(conElement);
        }

        if (myForceConfigureJDK) {
          root.setAttribute(FORCED_JDK, String.valueOf(true));
        }
        return root;
      }
    }
    return null;
  }

  public void loadState(Element state) {
    for (Object o : state.getChildren(LIBELEMENT)) {
      myEclipseUrls.add(((Element)o).getAttributeValue(VALUE_ATTR));
    }

    for (Object o : state.getChildren(VARELEMENT)) {
      myEclipseVariablePaths.put(((Element)o).getAttributeValue(VAR_ATTRIBUTE), ((Element)o).getAttributeValue(VALUE_ATTR));
    }

    for (Object o : state.getChildren(CONELEMENT)) {
      myUnknownCons.add(((Element)o).getAttributeValue(VALUE_ATTR));
    }

    final String forcedJdk = state.getAttributeValue(FORCED_JDK);
    myForceConfigureJDK = forcedJdk != null && Boolean.parseBoolean(forcedJdk);
  }
}
