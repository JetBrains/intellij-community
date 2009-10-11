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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.DirtBuilder;
import com.intellij.openapi.vcs.changes.VcsGuess;
import com.intellij.openapi.vcs.impl.projectlevelman.NewMappings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public abstract class DefaultVcsRootPolicy {
  public static DefaultVcsRootPolicy getInstance(Project project) {
    return ServiceManager.getService(project, DefaultVcsRootPolicy.class);
  }

  public abstract void addDefaultVcsRoots(final NewMappings mappingList, AbstractVcs vcs, List<VirtualFile> result);

  public abstract boolean matchesDefaultMapping(final VirtualFile file, final Object matchContext);

  @Nullable
  public abstract Object getMatchContext(final VirtualFile file);

  @Nullable
  public abstract VirtualFile getVcsRootFor(final VirtualFile file);

  public abstract void markDefaultRootsDirty(final DirtBuilder builder, VcsGuess vcsGuess);
}
