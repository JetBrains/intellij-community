/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.compiler.make.BuildParticipantProvider;
import com.intellij.openapi.compiler.make.BuildParticipant;
import com.intellij.openapi.module.Module;
import org.jetbrains.idea.devkit.module.PluginModuleType;

/**
 * @author nik
 */
public class PluginBuildParticipantProvider extends BuildParticipantProvider {

  public BuildParticipant[] getParticipants(final Module module) {
    if (module.getModuleType() != PluginModuleType.getInstance()) {
      return BuildParticipant.EMPTY_ARRAY;
    }
    return new BuildParticipant[] {new PluginBuildParticipant(module)};
  }
}
