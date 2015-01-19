/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
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
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/dslActivation.xml", roamingType = RoamingType.DISABLED)
)
public class DslActivationStatus implements PersistentStateComponent<Element> {
  private final Map<VirtualFile, String> myStatus = new THashMap<VirtualFile, String>();
  private static final String ENABLED = "enabled";

  public static DslActivationStatus getInstance() {
    return ServiceManager.getService(DslActivationStatus.class);
  }

  public synchronized void activateUntilModification(@NotNull VirtualFile vfile) {
    myStatus.put(vfile, ENABLED);
  }

  public synchronized void disableFile(@NotNull VirtualFile vfile, @NotNull String error) {
    myStatus.put(vfile, error);
  }

  @Nullable
  public synchronized String getInactivityReason(VirtualFile file) {
    String status = myStatus.get(file);
    return ENABLED.equals(status) ? null : status;
  }

  public synchronized boolean isActivated(VirtualFile file) {
    final String status = myStatus.get(file);
    if (status == null) {
      myStatus.put(file, ENABLED);
      return true;
    }
    return ENABLED.equals(status);
  }

  @Nullable
  @Override
  public synchronized Element getState() {
    Element root = new Element("x");
    for (Map.Entry<VirtualFile, String> entry : myStatus.entrySet()) {
      VirtualFile file = entry.getKey();
      String status = entry.getValue();
      Element element = new Element("file");
      root.addContent(element);
      element.setAttribute("url", file.getUrl());
      if (!ENABLED.equals(status)) {
        element.setAttribute("status", status);
      }
    }
    return root;
  }

  @Override
  public synchronized void loadState(Element state) {
    List<Element> children = state.getChildren("file");
    for (Element element : children) {
      String url = element.getAttributeValue("url", "");
      String status = element.getAttributeValue("status");
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null) {
        myStatus.put(file, StringUtil.isNotEmpty(status) ? status : ENABLED);
      }
    }
  }
}
