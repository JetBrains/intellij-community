// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.dom.AntDomIncludingDirective;
import com.intellij.lang.ant.dom.AntDomProject;
import com.intellij.lang.ant.dom.AntDomTarget;
import com.intellij.lang.ant.dom.TargetResolver;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiCachedValueImpl;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AntBuildModelImpl implements AntBuildModelBase {
  private final AntBuildFile myFile;
  private final CachedValue<List<AntBuildTargetBase>> myTargets;

  public AntBuildModelImpl(final AntBuildFile buildFile) {
    myFile = buildFile;
    myTargets = new PsiCachedValueImpl.Soft<>(PsiManager.getInstance(myFile.getProject()), ()-> ReadAction.compute(()-> {
      final Pair<List<AntBuildTargetBase>, Collection<Object>> result = getTargetListImpl(this);
      return CachedValueProvider.Result.create(result.getFirst(), ArrayUtil.toObjectArray(result.getSecond()));
    }));
  }

  @Override
  @Nullable
  public String getDefaultTargetName() {
    final AntDomProject antDomProject = getAntProject();
    if (antDomProject != null) {
      return antDomProject.getDefaultTarget().getRawText();
    }
    return "";
  }

  @Override
  @Nullable
  public @NlsSafe String getName() {
    final AntDomProject project = getAntProject();
    return project != null? project.getName().getRawText() : null;
  }

  @Override
  public AntBuildTarget[] getTargets() {
    return myTargets.getValue().toArray(AntBuildTargetBase.EMPTY_ARRAY);
  }

  @Override
  public AntBuildTarget[] getFilteredTargets() {
    final List<AntBuildTargetBase> filtered = new ArrayList<>();
    for (final AntBuildTargetBase buildTarget : myTargets.getValue()) {
      if (myFile.isTargetVisible(buildTarget)) {
        filtered.add(buildTarget);
      }
    }
    return (filtered.isEmpty()) ? AntBuildTargetBase.EMPTY_ARRAY : filtered.toArray(AntBuildTargetBase.EMPTY_ARRAY);
  }

  @Override
  @Nullable
  public @NonNls String getDefaultTargetActionId() {
    if (StringUtil.isEmptyOrSpaces(getDefaultTargetName())) {
      return null;
    }
    final String modelName = getName();
    if (StringUtil.isEmptyOrSpaces(modelName)) {
      return null;
    }
    return AntConfiguration.getActionIdPrefix(getBuildFile().getProject()) + "_" + modelName.trim() + "_" + TargetAction.getDefaultTargetName();
  }

  @Override
  public AntBuildFileBase getBuildFile() {
    return (AntBuildFileBase)myFile;
  }

  @Override
  @Nullable
  public AntBuildTargetBase findTarget(final String name) {
    for (AntBuildTargetBase target : myTargets.getValue()) {
      if (Comparing.strEqual(target.getName(), name)) {
        return target;
      }
    }
    return null;
  }

  @Override
  @Nullable
  public BuildTask findTask(final String targetName, final String taskName) {
    final AntBuildTargetBase buildTarget = findTarget(targetName);
    return (buildTarget == null) ? null : buildTarget.findTask(taskName);
  }

  @Override
  public AntDomProject getAntProject() {
    return AntSupport.getAntDomProject(getBuildFile().getAntFile());
  }

  @Override
  public boolean hasTargetWithActionId(final String id) {
    return StreamEx.of(myTargets.getValue()).map(AntBuildTargetBase::getActionId).has(id);
  }

  // todo: return list of dependent psi files as well
  private static Pair<List<AntBuildTargetBase>, Collection<Object>> getTargetListImpl(final AntBuildModelBase model) {
    final List<AntBuildTargetBase> list = new ArrayList<>();
    final Set<Object> dependencies = new HashSet<>();

    final AntDomProject project = model.getAntProject();
    if (project != null) {
      final AntBuildFile buildFile = model.getBuildFile();
      final XmlFile xmlFile = buildFile.getAntFile();
      dependencies.add(xmlFile != null? xmlFile : PsiModificationTracker.MODIFICATION_COUNT);

      final VirtualFile sourceFile = buildFile.getVirtualFile();
      new Object() {
        private boolean myIsImported = false;
        private final Set<VirtualFile> myProcessed = new HashSet<>();
        private AntDomTarget myDefaultTarget = null;

        private void fillTargets(List<? super AntBuildTargetBase> list, AntBuildModelBase model, AntDomProject project, VirtualFile sourceFile) {
          if (myProcessed.contains(sourceFile)) {
            return;
          }
          myProcessed.add(sourceFile);
          if (!myIsImported) {
            final TargetResolver.Result result = project.getDefaultTarget().getValue();
            if (result != null) {
              final Pair<AntDomTarget,String> targetWithName = result.getResolvedTarget(project.getDefaultTarget().getRawText());
              myDefaultTarget = Pair.getFirst(targetWithName);
            }
          }
          for (final AntDomTarget target : project.getDeclaredTargets()) {
            list.add(new AntBuildTargetImpl(target, model, sourceFile, myIsImported, target.equals(myDefaultTarget)));
          }

          myIsImported = true;

          final Iterable<AntDomIncludingDirective> allIncludes = ContainerUtil.concat(project.getDeclaredImports(),
                                                                                      project.getDeclaredIncludes());
          for (AntDomIncludingDirective incl : allIncludes) {
            final PsiFileSystemItem includedFile = incl.getFile().getValue();
            if (includedFile instanceof PsiFile) {
              final PsiFile included = includedFile.getContainingFile().getOriginalFile();
              dependencies.add(included);
              final AntDomProject includedProject = AntSupport.getAntDomProject((PsiFile)includedFile);
              if (includedProject != null) {
                fillTargets(list, model, includedProject, included.getVirtualFile());
              }
            }
            else {
              if (includedFile == null) {
                // if not resolved yet, it's possible that the file will be created later,
                // thus we need to recalculate the cached value
                dependencies.add(PsiModificationTracker.MODIFICATION_COUNT);
              }
            }
          }

        }
      }.fillTargets(list, model, project, sourceFile);
    }
    if (dependencies.isEmpty()) {
      dependencies.add(PsiModificationTracker.MODIFICATION_COUNT);
    }
    return new Pair<>(list, dependencies);
  }

}
