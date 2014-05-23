/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@State(
  name = "DslActivationStatus",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/dslActivation.xml")
  }
)
public class DslActivationStatus implements ApplicationComponent, PersistentStateComponent<Element> {
  private final Map<VirtualFile, String> myStatus = new THashMap<VirtualFile, String>();
  private static final String ENABLED = "enabled";

  public static DslActivationStatus getInstance() {
    return ApplicationManager.getApplication().getComponent(DslActivationStatus.class);
  }

  public void activateUntilModification(@NotNull VirtualFile vfile) {
    myStatus.put(vfile, ENABLED);
  }

  public void disableFile(@NotNull VirtualFile vfile, @NotNull String error) {
    myStatus.put(vfile, error);
  }

  @Nullable
  public String getInactivityReason(VirtualFile file) {
    String status = myStatus.get(file);
    return status == null || status == ENABLED ? null : status;
  }

  public boolean isActivated(VirtualFile file) {
    return myStatus.get(file) == ENABLED;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "DslActivationStatus";
  }

  @Nullable
  @Override
  public Element getState() {
    Element root = new Element("x");
    for (Map.Entry<VirtualFile, String> entry : myStatus.entrySet()) {
      VirtualFile file = entry.getKey();
      String status = entry.getValue();
      Element element = new Element("file");
      root.addContent(element);
      element.setAttribute("url", file.getUrl());
      element.setAttribute("status", (status == ENABLED ? "" : status));
    }
    return root;
  }

  @Override
  public void loadState(Element state) {
    List<Element> children = state.getChildren("file");
    for (Element element : children) {
      String url = element.getAttributeValue("url", "");
      String status = element.getAttributeValue("status", ENABLED);
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null) {
        myStatus.put(file, status);
      }
    }
  }

  @Override
  public void initComponent() {

  }

  @Override
  public void disposeComponent() {

  }
}
