// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ShowExternalDataStats extends DumbAwareAction {

  public ShowExternalDataStats() {
    super("Show external project data stats");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    StringBuilder message = new StringBuilder("External Gradle data stats\n");

    final Collection<ExternalProjectInfo> data =
      ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID);

    for (ExternalProjectInfo info : data) {
      final String path = info.getExternalProjectPath();

      final DataNode<ProjectData> root = info.getExternalProjectStructure();
      final Map<String, Stats> stats = Stats.calculateFor(root);

      message.append("\nProject path: [").append(path).append("]\n");
      stats.forEach((cl, stat) -> {
        message.append("Class [").append(cl).append("] - ").append(stat.toString()).append('\n');
      });
    }

    Messages.showMessageDialog(project, message.toString(), "Data Stats", Messages.getInformationIcon());
  }
}

class Stats {

  public static final Logger LOG = Logger.getInstance(Stats.class);
  private long myNumberOfObjects;
  private final long mySerializedDataSize;

  public Stats(long numberOfObjects, long serializedDataSize) {
    myNumberOfObjects = numberOfObjects;
    mySerializedDataSize = serializedDataSize;
  }

  @Override
  public String toString() {
    return "Objects: [" + myNumberOfObjects + "] Serialized data size: " + mySerializedDataSize + " bytes ";
  }

  @NotNull
  public static Map<String, Stats> calculateFor(DataNode<ProjectData> root) {
    final MultiMap<Key<?>, DataNode<?>> grouped = ExternalSystemApiUtil.recursiveGroup(Collections.singleton(root));

    final Map<String, Stats> result = new LinkedHashMap<>();

    for (Map.Entry<Key<?>, Collection<DataNode<?>>> entry : grouped.entrySet()) {
      final Key<?> key = entry.getKey();
      final Collection<DataNode<?>> nodes = entry.getValue();

      long totalBytesLength = 0;
      for (DataNode<?> node : nodes) {
        try {
          totalBytesLength += node.getDataBytes().length;
          node.getData(); // releases data buffer
        } catch (IOException e) {
          LOG.error("Failed to count node bytes", e);
        }
      }

      result.put(key.getDataType(), new Stats(nodes.size(), totalBytesLength));
    }

    return result;
  }
}
