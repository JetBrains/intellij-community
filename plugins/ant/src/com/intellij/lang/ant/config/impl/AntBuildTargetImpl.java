package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

public class AntBuildTargetImpl implements AntBuildTargetBase {

  private final AntBuildModelBase myModel;
  private final VirtualFile myFile;
  private final boolean myIsDefault;
  private final int myHashCode;
  private final String myName;
  private final String myDisplayName;
  private final String myDescription;
  private final Project myProject;
  private final int myTextOffset;

  public AntBuildTargetImpl(final AntTarget target, final AntBuildModelBase buildModel, final VirtualFile sourceFile, final boolean isImported, final boolean isDefault) {
    myModel = buildModel;
    myFile = sourceFile;
    myIsDefault = isDefault;
    myHashCode = target.hashCode();
    myName = target.getName();
    myDisplayName = isImported ? target.getQualifiedName() : target.getName();
    myProject = target.getProject();
    myTextOffset = target.getTextOffset();
    
    final String desc = target.getDescription();
    myDescription = (desc != null && desc.trim().length() > 0) ? desc : null;
  }

  public int hashCode() {
    return myHashCode;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof AntBuildTargetImpl)) {
      return false;
    }
    final AntBuildTargetImpl that = (AntBuildTargetImpl)obj;
    return Comparing.equal(myName, that.myName) && myFile.equals(that.myFile);
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Nullable
  public String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  public String getNotEmptyDescription() {
    return myDescription;
  }

  public boolean isDefault() {
    return myIsDefault;
  }

  public VirtualFile getContainingFile() {
    return myFile;
  }

  public AntBuildModelBase getModel() {
    return myModel;
  }

  @Nullable
  public String getActionId() {
    final String modelName = myModel.getName();
    if (modelName == null || modelName.length() == 0) {
      return null;
    }
    final StringBuilder name = StringBuilderSpinAllocator.alloc();
    try {
      name.append(AntConfiguration.getActionIdPrefix(myModel.getBuildFile().getProject()));
      name.append("_");
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
    final AntFile antFile = AntSupport.toAntFile(myFile, myProject);
    if (antFile != null) {
      final AntTarget target = antFile.getAntProject().getTarget(myName);
      if (target != null) {
        for (final PsiElement element : target.getChildren()) {
          if (element instanceof AntTask) {
            final AntTask task = (AntTask)element;
            if (taskName.equals(task.getSourceElement().getName())) {
              return new BuildTask(this, task);
            }
          }
        }
      }
    }
    return null;
  }

  public OpenFileDescriptor getOpenFileDescriptor() {
    return (myFile == null) ? null : new OpenFileDescriptor(myProject, myFile, myTextOffset);
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
    ExecutionHandler.runBuild((AntBuildFileBase)buildFile, targets, null, dataContext, buildListener);
  }
}
