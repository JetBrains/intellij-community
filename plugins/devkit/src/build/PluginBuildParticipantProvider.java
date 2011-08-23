/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.compiler.make.BuildParticipantProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.Collection;
import java.util.Collections;

/**
 * @author nik
 */
public class PluginBuildParticipantProvider extends BuildParticipantProvider {
  public Collection<PluginBuildParticipant> getParticipants(final Module module) {
    if (ModuleType.get(module) != PluginModuleType.getInstance()) {
      return Collections.emptyList();
    }

    final PluginBuildConfiguration configuration = PluginBuildConfiguration.getInstance(module);
    return configuration != null ? Collections.singletonList(configuration.getBuildParticipant())
                                 : Collections.<PluginBuildParticipant>emptyList();
  }
}
