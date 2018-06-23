// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.PerformanceTrace;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;

public class GradleTraceTest extends GradleImportingTestCase {

  @Test
  public void testEmptyImport() {
    long testStartTime = System.currentTimeMillis();
    importProjectUsingSingeModulePerGradleProject();
    long importDoneTime = System.currentTimeMillis();

    ProjectDataManager manager = ProjectDataManager.getInstance();
    final Collection<ExternalProjectInfo> data = manager.getExternalProjectsData(myProject, GradleConstants.SYSTEM_ID);

    assertSize(1, data);

    final DataNode<ProjectData> rootNode = data.iterator().next().getExternalProjectStructure();
    final DataNode<PerformanceTrace> traceDataNode = ExternalSystemApiUtil.find(rootNode, PerformanceTrace.TRACE_NODE_KEY);

    assertNotNull(traceDataNode);

    final Map<String, Long> trace = traceDataNode.getData().getPerformanceTrace();

    assertTrue(trace.size() > 20);
    assertTrue(sum(trace.values()) > 0);

    assertTracedTimePercentAtLeast(trace, importDoneTime - testStartTime, 50);
  }

  @Test
  public void testEmptyImportPerSourceSet() {
    long testStartTime = System.currentTimeMillis();
    importProject();
    long importDoneTime = System.currentTimeMillis();

    ProjectDataManager manager = ProjectDataManager.getInstance();
    final Collection<ExternalProjectInfo> data = manager.getExternalProjectsData(myProject, GradleConstants.SYSTEM_ID);

    assertSize(1, data);

    final DataNode<ProjectData> rootNode = data.iterator().next().getExternalProjectStructure();
    final DataNode<PerformanceTrace> traceDataNode = ExternalSystemApiUtil.find(rootNode, PerformanceTrace.TRACE_NODE_KEY);

    assertNotNull(traceDataNode);

    final Map<String, Long> trace = traceDataNode.getData().getPerformanceTrace();

    assertTrue(trace.size() > 20);
    assertTrue(sum(trace.values()) > 0);

    final int threshold = 85;
    assertTracedTimePercentAtLeast(trace, importDoneTime - testStartTime, 85);
  }

  private static void assertTracedTimePercentAtLeast(@NotNull final Map<String, Long> trace, long time, int threshold) {
    final long tracedTime = trace.get("Gradle data obtained")
                            + trace.get("Gradle project data processed")
                            + trace.get("Data import total");

    double percent = (double)tracedTime / time * 100;
    assertTrue( String.format("Test time [%d] traced time [%d], percentage [%.2f]", time, tracedTime, percent), percent > threshold && percent < 100);
  }


  private static long sum(@NotNull Collection<Long> values) {
    long result = 0;
    for (Long value : values) {
      if (value != null) {
        result += value;
      }
    }
    return result;
  }
}
