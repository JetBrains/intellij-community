/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseModuleManager;
import org.jetbrains.jps.eclipse.model.JpsEclipseClasspathSerializer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@State(name = "EclipseModuleManager")
public class EclipseModuleManagerImpl implements EclipseModuleManager, PersistentStateComponent<Element>, StateStorageChooserEx {
  @NonNls private static final String VALUE_ATTR = "value";
  @NonNls private static final String VARELEMENT = "varelement";
  @NonNls private static final String VAR_ATTRIBUTE = "var";
  @NonNls private static final String CONELEMENT = "conelement";
  @NonNls private static final String FORCED_JDK = "forced_jdk";
  @NonNls private static final String SRC_DESCRIPTION = "src_description";
  @NonNls private static final String EXPECTED_POSITION = "expected_position";
  @NonNls private static final String SRC_FOLDER = "src_folder";
  private CachedXmlDocumentSet myDocumentSet;
  private final Map<String, String> myEclipseVariablePaths = new LinkedHashMap<>();
  private final Set<String> myEclipseUrls = new LinkedHashSet<>();
  private final Set<String> myUnknownCons = new LinkedHashSet<>();
  private boolean myForceConfigureJDK;
  @NonNls private static final String SRC_PREFIX = "src:";
  @NonNls private static final String SRC_LINK_PREFIX = "linksrc:";
  @NonNls private static final String LINK_PREFIX = "link:";
  @NonNls private static final String PREFIX_ATTR = "kind";
  private final Module myModule;
  @NonNls private static final String LIBELEMENT = "libelement";
  private int myExpectedModuleSourcePlace;
  private Map<String, Integer> mySrcPlace = new LinkedHashMap<>();
  private String myInvalidJdk;

  private Set<String> myKnownCons = new LinkedHashSet<>();

  public EclipseModuleManagerImpl(Module module) {
    myModule = module;
  }

  public static EclipseModuleManagerImpl getInstance(Module module) {
    return ModuleServiceManager.getService(module, EclipseModuleManagerImpl.class);
  }

  public static boolean isEclipseStorage(@NotNull Module module) {
    return JpsEclipseClasspathSerializer.CLASSPATH_STORAGE_ID.equals(ClassPathStorageUtil.getStorageType(module));
  }

  @Override
  public void setInvalidJdk(String invalidJdk) {
    myInvalidJdk = invalidJdk;
  }

  @Override
  public String getInvalidJdk() {
    return myInvalidJdk;
  }

  @Override
  public void registerCon(String name) {
    myKnownCons.add(name);
  }

  @Override
  public String[] getUsedCons() {
    return ArrayUtil.toStringArray(myKnownCons);
  }

  @Nullable
  public CachedXmlDocumentSet getDocumentSet() {
    return myDocumentSet;
  }

  public void setDocumentSet(@Nullable CachedXmlDocumentSet documentSet) {
    myDocumentSet = documentSet;
  }

  @Override
  public void registerEclipseVariablePath(String path, String var) {
    myEclipseVariablePaths.put(path, var);
  }

  @Override
  public void registerEclipseSrcVariablePath(String path, String var) {
    myEclipseVariablePaths.put(SRC_PREFIX + path, var);
  }

  @Override
  public void registerEclipseLinkedSrcVarPath(String path, String var) {
    myEclipseVariablePaths.put(SRC_LINK_PREFIX + path, var);
  }

  @Override
  public String getEclipseLinkedSrcVariablePath(String path) {
    return myEclipseVariablePaths.get(SRC_LINK_PREFIX + path);
  }

  @Override
  public void registerEclipseLinkedVarPath(String path, String var) {
    myEclipseVariablePaths.put(LINK_PREFIX + path, var);
  }

   @Override
   public String getEclipseLinkedVarPath(String path) {
    return myEclipseVariablePaths.get(LINK_PREFIX + path);
  }

  @Override
  public String getEclipseVariablePath(String path) {
    return myEclipseVariablePaths.get(path);
  }

  @Override
  public String getEclipseSrcVariablePath(String path) {
    return myEclipseVariablePaths.get(SRC_PREFIX + path);
  }

  @Override
  public void registerUnknownCons(String con) {
    myUnknownCons.add(con);
  }

  @NotNull
  @Override
  public Set<String> getUnknownCons() {
    return myUnknownCons;
  }

  @Override
  public boolean isForceConfigureJDK() {
    return myForceConfigureJDK;
  }

  @Override
  public void setForceConfigureJDK() {
    myForceConfigureJDK = true;
    myExpectedModuleSourcePlace++;
  }

  @Override
  public void registerEclipseLibUrl(String url) {
    myEclipseUrls.add(url);
  }

  @Override
  public boolean isEclipseLibUrl(String url) {
    return myEclipseUrls.contains(url);
  }

  @NotNull
  @Override
  public Resolution getResolution(@NotNull Storage storage, @NotNull StateStorageOperation operation) {
    if (operation == StateStorageOperation.READ) {
      return Resolution.DO;
    }

    if (isEclipseStorage(myModule) || (myEclipseUrls.isEmpty() && myEclipseVariablePaths.isEmpty() && !myForceConfigureJDK && myUnknownCons.isEmpty())) {
      return Resolution.CLEAR;
    }
    else {
      return Resolution.DO;
    }
  }

  @Override
  public Element getState() {
    Element root = new Element("EclipseModuleSettings");

    for (String eclipseUrl : myEclipseUrls) {
      root.addContent(new Element(LIBELEMENT).setAttribute(VALUE_ATTR, eclipseUrl));
    }

    for (String var : myEclipseVariablePaths.keySet()) {
      Element varElement = new Element(VARELEMENT);
      if (var.startsWith(SRC_PREFIX)) {
        varElement.setAttribute(VAR_ATTRIBUTE, StringUtil.trimStart(var, SRC_PREFIX));
        varElement.setAttribute(PREFIX_ATTR, SRC_PREFIX);
      }
      else if (var.startsWith(SRC_LINK_PREFIX)) {
        varElement.setAttribute(VAR_ATTRIBUTE, StringUtil.trimStart(var, SRC_LINK_PREFIX));
        varElement.setAttribute(PREFIX_ATTR, SRC_LINK_PREFIX);
      }
      else if (var.startsWith(LINK_PREFIX)) {
        varElement.setAttribute(VAR_ATTRIBUTE, StringUtil.trimStart(var, LINK_PREFIX));
        varElement.setAttribute(PREFIX_ATTR, LINK_PREFIX);
      }
      else {
        varElement.setAttribute(VAR_ATTRIBUTE, var);
      }
      varElement.setAttribute(VALUE_ATTR, myEclipseVariablePaths.get(var));
      root.addContent(varElement);
    }

    for (String unknownCon : myUnknownCons) {
      root.addContent(new Element(CONELEMENT).setAttribute(VALUE_ATTR, unknownCon));
    }

    if (myForceConfigureJDK) {
      root.setAttribute(FORCED_JDK, String.valueOf(true));
    }

    Element srcDescriptionElement = new Element(SRC_DESCRIPTION);
    srcDescriptionElement.setAttribute(EXPECTED_POSITION, String.valueOf(myExpectedModuleSourcePlace));
    for (String srcUrl : mySrcPlace.keySet()) {
      Element srcFolder = new Element(SRC_FOLDER);
      srcFolder.setAttribute(VALUE_ATTR, srcUrl);
      srcFolder.setAttribute(EXPECTED_POSITION, mySrcPlace.get(srcUrl).toString());
      srcDescriptionElement.addContent(srcFolder);
    }
    root.addContent(srcDescriptionElement);

    return root;
  }

  @Override
  public void loadState(Element state) {
    myEclipseUrls.clear();
    myEclipseVariablePaths.clear();
    myUnknownCons.clear();
    mySrcPlace.clear();
    myKnownCons.clear();

    for (Element o : state.getChildren(LIBELEMENT)) {
      myEclipseUrls.add(o.getAttributeValue(VALUE_ATTR));
    }

    for (Element o : state.getChildren(VARELEMENT)) {
      myEclipseVariablePaths.put(o.getAttributeValue(VAR_ATTRIBUTE), o.getAttributeValue(PREFIX_ATTR, "") + o.getAttributeValue(VALUE_ATTR));
    }

    for (Element o : state.getChildren(CONELEMENT)) {
      myUnknownCons.add(o.getAttributeValue(VALUE_ATTR));
    }

    myForceConfigureJDK = Boolean.parseBoolean(state.getAttributeValue(FORCED_JDK, "false"));

    Element srcDescriptionElement = state.getChild(SRC_DESCRIPTION);
    if (srcDescriptionElement != null) {
      myExpectedModuleSourcePlace = Integer.parseInt(srcDescriptionElement.getAttributeValue(EXPECTED_POSITION));
      for (Element o : srcDescriptionElement.getChildren(SRC_FOLDER)) {
        mySrcPlace.put(o.getAttributeValue(VALUE_ATTR), Integer.parseInt(o.getAttributeValue(EXPECTED_POSITION)));
      }
    }
  }

  @Override
  public void setExpectedModuleSourcePlace(int expectedModuleSourcePlace) {
    myExpectedModuleSourcePlace = expectedModuleSourcePlace;
  }

  @Override
  public boolean isExpectedModuleSourcePlace(int expectedPlace) {
    return myExpectedModuleSourcePlace == expectedPlace;
  }

  @Override
  public void registerSrcPlace(String srcUrl, int placeIdx) {
    mySrcPlace.put(srcUrl, placeIdx);
  }

  @Override
  public int getSrcPlace(String srcUtl) {
    Integer v = mySrcPlace.get(srcUtl);
    return v == null ? -1 : v;
  }
}
