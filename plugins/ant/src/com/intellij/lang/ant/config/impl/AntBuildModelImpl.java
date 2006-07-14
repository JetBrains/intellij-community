package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildModel;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntBuildModelImpl implements AntBuildModel {

  private final AntBuildFile myFile;

  public AntBuildModelImpl(final AntBuildFile buildFile) {
    myFile = buildFile;
  }

  @Nullable
  public String getDefaultTargetName() {
    final AntTarget target = getAntProject().getDefaultTarget();
    return (target == null) ? "" : target.getName();
  }

  @Nullable
  public String getName() {
    return getAntProject().getName();
  }

  public AntBuildTarget[] getTargets() {
    final List<AntBuildTarget> list = getTargetsList();
    return list.toArray(new AntBuildTarget[list.size()]);
  }

  public AntBuildTarget[] getFilteredTargets() {
    final List<AntBuildTarget> filtered = new ArrayList<AntBuildTarget>();
    for (final AntBuildTarget buildTarget : getTargetsList()) {
      if (myFile.isTargetVisible(buildTarget)) {
        filtered.add(buildTarget);
      }
    }
    return (filtered.size() == 0) ? AntBuildTarget.EMPTY_ARRAY : filtered.toArray(new AntBuildTarget[filtered.size()]);
  }

  @Nullable
  public String getDefaultTargetActionId() {
    if (getDefaultTargetName() == null) return null;
    final String projectName = getName();
    if (projectName == null || projectName.trim().length() == 0) return null;
    return TargetAction.ACTION_ID_PREFIX + projectName;

  }

  public AntBuildFile getBuildFile() {
    return myFile;
  }

  @Nullable
  public AntBuildTarget findTarget(final String name) {
    final AntTarget antTarget = getAntProject().getTarget(name);
    if (antTarget != null) {
      for (final AntBuildTarget buildTarget : getTargetsList()) {
        if (buildTarget.getAntTarget() == antTarget) {
          return buildTarget;
        }
      }
    }
    return null;
  }

  @Nullable
  public BuildTask findTask(final String targetName, final String taskName) {
    final AntBuildTarget buildTarget = findTarget(targetName);
    return (buildTarget == null) ? null : buildTarget.findTask(taskName);
  }

  public AntProject getAntProject() {
    return myFile.getAntFile().getAntProject();
  }

  private List<AntBuildTarget> getTargetsList() {
    final AntTarget[] targets = getAntProject().getTargets();
    final List<AntBuildTarget> list = new ArrayList<AntBuildTarget>(targets.length);
    for (final AntTarget target : targets) {
      list.add(new AntBuildTargetImpl(target, this));
    }
    return list;
  }
}
