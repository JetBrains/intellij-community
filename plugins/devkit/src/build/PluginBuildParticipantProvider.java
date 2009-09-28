/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.compiler.make.BuildParticipantProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public class PluginBuildParticipantProvider extends BuildParticipantProvider<PluginBuildParticipant> {
  public Collection<PluginBuildParticipant> getParticipants(final Module module) {
    if (module.getModuleType() != PluginModuleType.getInstance()) {
      return Collections.emptyList();
    }

    final PluginBuildConfiguration configuration = PluginBuildConfiguration.getInstance(module);
    return configuration != null ? Collections.singletonList(configuration.getBuildParticipant())
                                 : Collections.<PluginBuildParticipant>emptyList();
  }
}
