/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleServiceManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EclipseModuleManager {
  private CachedXmlDocumentSet myDocumentSet;
  private final Map<String, String> myEclipseVariablePaths = new HashMap<String, String>();
  private final Set<String> myEclipseUrls = new HashSet<String>();
  private final Set<String> myUnknownCons = new HashSet<String>();
  private boolean myForceConfigureJDK = false;
  private static final String SRC_PREFIX = "src:";
  private static final String SRC_LINK_PREFIX = "linksrc:";

  public EclipseModuleManager(Module module) {}

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
}
