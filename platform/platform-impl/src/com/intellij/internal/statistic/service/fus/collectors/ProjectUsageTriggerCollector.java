// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
// see example:
// class MyProjectActionUsageTriggerCollector: ProjectUsageTriggerCollector() {
//  override fun getGroupId(): String = MY_GROUP_ID
//
//  companion object {
//    fun trigger(project: Project, featureId: String) {
//      FUSProjectUsageTrigger.getInstance(project).trigger(MyProjectActionUsageTriggerCollector::class.java, featureId)
//    }}}
//
//  and invoke it:  MyProjectActionUsageTriggerCollector.trigger(project, "my.action.performed")

public abstract class ProjectUsageTriggerCollector extends ProjectUsagesCollector implements FUStatisticsDifferenceSender {
  @NotNull
  @Override
  public final Set<UsageDescriptor> getUsages(@NotNull Project project) {
    Map<String, Integer> data = FUSProjectUsageTrigger.getInstance(project).getData(getGroupId());

    return ContainerUtil.map2Set(data.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
  }
}
