/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.internal.statistic.persistence;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

@State(
  name = "StatisticsApplicationUsages",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/statistics.application.usages.xml"
    )}
)
public class ApplicationStatisticsPersistenceComponent extends ApplicationStatisticsPersistence
  implements ApplicationComponent, PersistentStateComponent<Element> {
  private boolean persistOnClosing = !ApplicationManager.getApplication().isUnitTestMode();

  private static final String TOKENIZER = ",";

  @NonNls
  private static final String GROUP_TAG = "group";
  @NonNls
  private static final String GROUP_NAME_ATTR = "name";

  @NonNls
  private static final String PROJECT_TAG = "project";
  @NonNls
  private static final String PROJECT_ID_ATTR = "id";
  @NonNls
  private static final String VALUES_ATTR = "values";

  public ApplicationStatisticsPersistenceComponent() {
  }

  public static ApplicationStatisticsPersistenceComponent getInstance() {
    return ApplicationManager.getApplication().getComponent(ApplicationStatisticsPersistenceComponent.class);
  }

  @Override
  public void loadState(final Element element) {
    List groups = element.getChildren(GROUP_TAG);

    for (Object group : groups) {
      Element groupElement = (Element)group;
      String groupName = groupElement.getAttributeValue(GROUP_NAME_ATTR);

      final GroupDescriptor groupDescriptor = GroupDescriptor.create(groupName);

      List projectsList = groupElement.getChildren(PROJECT_TAG);
      for (Object project : projectsList) {
        Element projectElement = (Element)project;
        String projectId = projectElement.getAttributeValue(PROJECT_ID_ATTR);
        String frameworks = projectElement.getAttributeValue(VALUES_ATTR);
        if (!StringUtil.isEmptyOrSpaces(projectId) && !StringUtil.isEmptyOrSpaces(frameworks)) {
          Set<UsageDescriptor> frameworkDescriptors = new HashSet<UsageDescriptor>();
          for (String key : StringUtil.split(frameworks, TOKENIZER)) {
            final UsageDescriptor descriptor = getUsageDescriptor(key);
            if (descriptor != null) frameworkDescriptors.add(descriptor);
          }
          getApplicationData(groupDescriptor).put(projectId, frameworkDescriptors);
        }
      }
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");

    for (Map.Entry<GroupDescriptor, Map<String, Set<UsageDescriptor>>> appData : getApplicationData().entrySet()) {
      Element groupElement = new Element(GROUP_TAG);
      groupElement.setAttribute(GROUP_NAME_ATTR, appData.getKey().getId());
      boolean isEmptyGroup = true;

      for (Map.Entry<String, Set<UsageDescriptor>> projectData : appData.getValue().entrySet()) {
        Element projectElement = new Element(PROJECT_TAG);
        projectElement.setAttribute(PROJECT_ID_ATTR, projectData.getKey());
        final Set<UsageDescriptor> projectDataValue = projectData.getValue();
        if (!projectDataValue.isEmpty()) {
          projectElement.setAttribute(VALUES_ATTR, joinUsages(projectDataValue));
          groupElement.addContent(projectElement);
          isEmptyGroup = false;
        }
      }

      if (!isEmptyGroup) {
        element.addContent(groupElement);
      }
    }

    return element;
  }

  private static UsageDescriptor getUsageDescriptor(String usage) {
    // for instance, usage can be: "_foo"(equals "_foo=1") or "_foo=2"
    try {
      final int i = usage.indexOf('=');
      if (i > 0 && i < usage.length() - 1) {
        String key = usage.substring(0, i).trim();
        String value = usage.substring(i + 1).trim();
        if (!StringUtil.isEmptyOrSpaces(key) && !StringUtil.isEmptyOrSpaces(value)) {
          try {
            final int count = Integer.parseInt(value);
            if (count > 0) {
              return new UsageDescriptor(key, count);
            }
          }
          catch (NumberFormatException ignored) {
          }
        }
      }
      return new UsageDescriptor(usage, 1);
    } catch (AssertionError e) {
      //escape loading of invalid usages
    }
    return null;
  }

  private static String joinUsages(@NotNull Set<UsageDescriptor> usages) {
    // for instance, usage can be: "_foo"(equals "_foo=1") or "_foo=2"
    return StringUtil.join(usages, new Function<UsageDescriptor, String>() {
      @Override
      public String fun(UsageDescriptor usageDescriptor) {
        final String key = usageDescriptor.getKey();
        final int value = usageDescriptor.getValue();

        return value > 1 ? key + "=" + value : key;
      }
    }, TOKENIZER);
  }

  @Override
  @NonNls
  @NotNull
  public String getComponentName() {
    return "ApplicationStatisticsPersistenceComponent";
  }

  @Override
  public void initComponent() {
    onAppClosing();
    onProjectClosing();
  }

  private void onProjectClosing() {
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
        if (project != null && project.isInitialized()) {
          if (persistOnClosing) {
            doPersistProjectUsages(project);
          }
        }
      }
    });
  }

  private static void doPersistProjectUsages(@NotNull Project project) {
    if (DumbService.isDumb(project)) return;
    for (UsagesCollector usagesCollector : Extensions.getExtensions(UsagesCollector.EP_NAME)) {
      if (usagesCollector instanceof AbstractApplicationUsagesCollector) {
        ((AbstractApplicationUsagesCollector)usagesCollector).persistProjectUsages(project);
      }
    }
  }

  private void onAppClosing() {
    final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();

    messageBus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
      }

      @Override
      public void appStarting(Project projectFromCommandLine) {
      }

      @Override
      public void projectFrameClosed() {
      }

      @Override
      public void projectOpenFailed() {
      }

      @Override
      public void welcomeScreenDisplayed() {
      }

      @Override
      public void appClosing() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          if (project.isInitialized()) {
            doPersistProjectUsages(project);
          }
        }
        persistOnClosing = false;
      }
    });
  }

  @Override
  public void disposeComponent() {
  }
}
