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

package com.intellij.openapi.vcs.statistics;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

@State(
  name = "VcsUsages",
  storages = {
    @Storage(
      id = "vcs",
      file = "$APP_CONFIG$/vcs.usages.xml"
    )}
)
public class VcsStatisticsPersistenceComponent extends VcsStatisticsPersistence
  implements ApplicationComponent, PersistentStateComponent<Element> {
  private static final String TOKENIZER = ",";

  @NonNls private static final String PROJECT_TAG = "project";
  @NonNls private static final String PROJECT_ID_ATTR = "id";
  @NonNls private static final String USAGES_ATTR = "usages";

  public VcsStatisticsPersistenceComponent() {
  }

  public static VcsStatisticsPersistenceComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(VcsStatisticsPersistenceComponent.class);
  }

  public void loadState(final Element element) {
    List projectsList = element.getChildren(PROJECT_TAG);
    for (Object project : projectsList) {
      Element projectElement = (Element)project;
      String projectId = projectElement.getAttributeValue(PROJECT_ID_ATTR);
      String vcs = projectElement.getAttributeValue(USAGES_ATTR);
      if (!StringUtil.isEmptyOrSpaces(projectId) && !StringUtil.isEmptyOrSpaces(vcs)) {
        Set<UsageDescriptor> vcsDescriptors = new HashSet<UsageDescriptor>();
        for (String key : StringUtil.split(vcs, TOKENIZER)) {
          vcsDescriptors.add(new UsageDescriptor(VcsUsagesCollector.createGroupDescriptor(), key, 1));
        }
        getVcsUsageMap().put(projectId, vcsDescriptors);
      }
    }
  }

  public Element getState() {
    Element element = new Element("state");

    for (Map.Entry<String, Set<UsageDescriptor>> vcsUsageEntry : getVcsUsageMap().entrySet()) {
      Element projectElement = new Element(PROJECT_TAG);
      projectElement.setAttribute(PROJECT_ID_ATTR, vcsUsageEntry.getKey());
      projectElement.setAttribute(USAGES_ATTR, joinUsages(vcsUsageEntry.getValue()));

      element.addContent(projectElement);
    }

    return element;
  }

  private static String joinUsages(@NotNull Set<UsageDescriptor> usages) {
    return StringUtil.join(usages, new Function<UsageDescriptor, String>() {
      @Override
      public String fun(UsageDescriptor usageDescriptor) {
        return usageDescriptor.getKey();
      }
    }, TOKENIZER);
  }

  @NotNull
  @NonNls
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("vcs.usages")};
  }

  @NotNull
  public String getPresentableName() {
    return "Vcs Usages";
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "VcsStatisticsPersistenceComponent";
  }

  public void initComponent() {
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
      }

      @Override
      public boolean canCloseProject(Project project) {
        return true;
      }

      @Override
      public void projectClosed(Project project) {
      }

      @Override
      public void projectClosing(Project project) {
        if (project != null) {
          VcsUsagesCollector.persistProjectUsages(project);
        }
      }
    });
  }

  public void disposeComponent() {
  }
}
