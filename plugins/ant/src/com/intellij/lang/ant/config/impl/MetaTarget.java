package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.*;
import com.intellij.lang.ant.config.actions.TargetAction;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.psi.PsiFile;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class MetaTarget implements AntBuildTargetBase {
  private final AntBuildFileBase myBuildFile;
  private final String[] myTargets;
  private final String myName;
  private final String myDescription;

  public MetaTarget(final AntBuildFileBase buildFile, final String displayName, final String[] targets) {
    myBuildFile = buildFile;
    myTargets = targets;
    myName = displayName;
    myDescription = AntBundle.message("meta.target.build.sequence.name.display.name", displayName);
  }

  public AntBuildFile getBuildFile() {
    return myBuildFile;
  }

  public String[] getTargetNames() {
    return myTargets;
  }

  public String getName() {
    return myName;
  }

  public String getNotEmptyDescription() {
    return myDescription;
  }

  public boolean isDefault() {
    return false;
  }

  public String getActionId() {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(TargetAction.ACTION_ID_PREFIX);
      builder.append('_');
      builder.append(myName);
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
    return builder.toString();
  }

  public AntBuildModelBase getModel() {
    return myBuildFile.getModel();
  }

  @Nullable
  public OpenFileDescriptor getOpenFileDescriptor() {
    return null;
  }

  @Nullable
  public BuildTask findTask(final String taskName) {
    return null;
  }

  public void run(DataContext dataContext, AntBuildListener buildListener) {
    ExecutionHandler.runBuild(myBuildFile, myTargets, null, dataContext, buildListener);
  }

  @Nullable
  public AntTarget getAntTarget() {
    return null;
  }

  public PsiFile getAntFile() {
    return myBuildFile.getAntFile();
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final MetaTarget that = (MetaTarget)o;

    if (!myBuildFile.equals(that.myBuildFile)) {
      return false;
    }
    if (!Arrays.equals(myTargets, that.myTargets)) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int code = myBuildFile.hashCode();
    for (String name : myTargets) {
      code += name.hashCode();
    }
    return code;
  }
}
