package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.AntBuildModel;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

public class AntBuildTargetImpl implements AntBuildTarget {

  private final AntTarget myTarget;
  private final AntBuildModel myModel;

  public AntBuildTargetImpl(final AntTarget target, final AntBuildModel buildModel) {
    myTarget = target;
    myModel = buildModel;
  }

  @Nullable
  public String getName() {
    return myTarget.getName();
  }

  @Nullable
  public String getNotEmptyDescription() {
    final String desc = myTarget.getDescription();
    return (desc != null && desc.trim().length() > 0) ? desc : null;
  }

  public boolean isDefault() {
    return myTarget == myModel.getAntProject().getDefaultTarget();
  }

  @Nullable
  public AntTarget getAntTarget() {
    return myTarget;
  }

  public AntFile getAntFile() {
    return myModel.getBuildFile().getAntFile();
  }

  public AntBuildModel getModel() {
    return myModel;
  }

  @Nullable
  public String getActionId() {
    String modelName = myModel.getName();
    if (modelName == null || modelName.trim().length() == 0) return null;
    final StringBuilder name = StringBuilderSpinAllocator.alloc();
    try {
      name.append(TargetAction.ACTION_ID_PREFIX);
      name.append(modelName);
      name.append('_');
      name.append(getName());
      return name.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(name);
    }
  }

  @Nullable
  public BuildTask findTask(final String taskName) {
    for (final PsiElement element : myTarget.getChildren()) {
      if (element instanceof AntTask) {
        final AntTask task = (AntTask)element;
        if (taskName.equals(task.getSourceElement().getName())) {
          return new BuildTask(this, task);
        }
      }
    }
    return null;
  }

  public OpenFileDescriptor getOpenFileDescriptor() {
    return new OpenFileDescriptor(myModel.getBuildFile().getProject(), myModel.getBuildFile().getVirtualFile(), myTarget.getTextOffset());
  }

  public void run(DataContext dataContext, AntBuildListener buildListener) {
    AntBuildModel model = getModel();
    if (model == null) {
      buildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      throw new IllegalStateException("Target '" + getName() + "' is invalid: model is null");
    }
    AntBuildFile buildFile = model.getBuildFile();
    if (buildFile == null) {
      buildListener.buildFinished(AntBuildListener.FAILED_TO_RUN, 0);
      throw new IllegalStateException("Target '" + getName() + "' is invalid: build file is null");
    }

    String[] targets = isDefault() ? ArrayUtil.EMPTY_STRING_ARRAY : new String[]{getName()};
    ExecutionHandler.runBuild(buildFile, targets, null, dataContext, buildListener);
  }
}
