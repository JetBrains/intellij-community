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
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.dom.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AntBuildModelImpl implements AntBuildModelBase {

  private final AntBuildFile myFile;

  public AntBuildModelImpl(final AntBuildFile buildFile) {
    myFile = buildFile;
  }

  @Nullable
  public String getDefaultTargetName() {
    final AntDomProject antDomProject = getAntProject();
    if (antDomProject != null) {
      return antDomProject.getDefaultTarget().getRawText();
    }
    return "";
  }

  @Nullable
  public String getName() {
    final AntDomProject project = getAntProject();
    return project != null? project.getName().getRawText() : null;
  }

  public AntBuildTarget[] getTargets() {
    final List<AntBuildTargetBase> list = getTargetsList();
    return list.toArray(new AntBuildTargetBase[list.size()]);
  }

  public AntBuildTarget[] getFilteredTargets() {
    final List<AntBuildTargetBase> filtered = new ArrayList<AntBuildTargetBase>();
    for (final AntBuildTargetBase buildTarget : getTargetsList()) {
      if (myFile.isTargetVisible(buildTarget)) {
        filtered.add(buildTarget);
      }
    }
    return (filtered.size() == 0) ? AntBuildTargetBase.EMPTY_ARRAY : filtered.toArray(new AntBuildTargetBase[filtered.size()]);
  }

  @Nullable
  public String getDefaultTargetActionId() {
    if (getDefaultTargetName() == null) {
      return null;
    }
    final String modelName = getName();
    if (modelName == null || modelName.trim().length() == 0) {
      return null;
    }
    return AntConfiguration.getActionIdPrefix(getBuildFile().getProject()) + modelName;

  }

  public AntBuildFileBase getBuildFile() {
    return (AntBuildFileBase)myFile;
  }

  @Nullable
  public AntBuildTargetBase findTarget(final String name) {
    return ApplicationManager.getApplication().runReadAction(new Computable<AntBuildTargetBase>() {
      @Nullable
      public AntBuildTargetBase compute() {
        return findTargetImpl(name, AntBuildModelImpl.this);
      }
    });
  }

  @Nullable
  public BuildTask findTask(final String targetName, final String taskName) {
    final AntBuildTargetBase buildTarget = findTarget(targetName);
    return (buildTarget == null) ? null : buildTarget.findTask(taskName);
  }

  public AntDomProject getAntProject() {
    return AntSupport.getAntDomProject(getBuildFile().getAntFile());
  }

  public boolean hasTargetWithActionId(final String id) {
    final List<AntBuildTargetBase> targetsList = getTargetsList();
    for (AntBuildTargetBase buildTarget : targetsList) {
      if (id.equals(buildTarget.getActionId())) return true;
    }
    return false;
  }

  private List<AntBuildTargetBase> getTargetsList() {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<AntBuildTargetBase>>() {
      public List<AntBuildTargetBase> compute() {
        return getTargetListImpl(AntBuildModelImpl.this);
      }
    });
  }

  @Nullable
  private static AntBuildTargetBase findTargetImpl(final String name, final AntBuildModelImpl model) {
    final List<AntBuildTargetBase> buildTargetBases = getTargetListImpl(model);
    for (AntBuildTargetBase targetBase : buildTargetBases) {
      if (Comparing.strEqual(targetBase.getName(), name)) {
        return targetBase;
      }
    }
    return null;
  }

  private static List<AntBuildTargetBase> getTargetListImpl(final AntBuildModelBase model) {
    final List<AntBuildTargetBase> list = new ArrayList<AntBuildTargetBase>();
    final AntDomProject project = model.getAntProject();
    final VirtualFile sourceFile = model.getBuildFile().getVirtualFile();
    if (project != null) {
      new Object() {
        private boolean myIsImported = false;
        private final Set<VirtualFile> myProcessed = new HashSet<VirtualFile>();
        private AntDomTarget myDefaultTarget = null;
                
        private void fillTargets(List<AntBuildTargetBase> list, AntBuildModelBase model, AntDomProject project, VirtualFile sourceFile) {
          if (myProcessed.contains(sourceFile)) {
            return;
          }
          myProcessed.add(sourceFile);
          if (!myIsImported) {
            final TargetResolver.Result result = project.getDefaultTarget().getValue();
            if (result != null) {
              final Pair<AntDomTarget,String> targetWithName = result.getResolvedTarget(project.getDefaultTarget().getRawText());
              myDefaultTarget = targetWithName != null? targetWithName.getFirst() : null;
            }
          }
          for (final AntDomTarget target : project.getDeclaredTargets()) {
            list.add(new AntBuildTargetImpl(target, model, sourceFile, myIsImported, target.equals(myDefaultTarget)));
          }
          
          myIsImported = true;
          
          final Iterable<AntDomIncludingDirective> allIncludes = ContainerUtil.concat((Iterable<AntDomImport>)project.getDeclaredImports(), (Iterable<? extends AntDomInclude>)project.getDeclaredIncludes());
          for (AntDomIncludingDirective incl : allIncludes) {
            final PsiFileSystemItem includedFile = incl.getFile().getValue();
            if (includedFile instanceof PsiFile) {
              final AntDomProject includedProject = AntSupport.getAntDomProject((PsiFile)includedFile);
              if (includedProject != null) {
                fillTargets(list, model, includedProject, includedFile.getContainingFile().getOriginalFile().getVirtualFile());
              }
            }

          }
    
        }
      }.fillTargets(list, model, project, sourceFile);
    }
    return list;
  }

}
