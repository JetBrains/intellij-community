// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ant.config.AntBuildTargetBase;
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.Nullable;

public final class BuildTask {
  public static final BuildTask[] EMPTY_ARRAY = new BuildTask[0];
  private final AntBuildTargetBase myTarget;
  private final @NlsSafe String myName;
  private final int myOffset;

  public BuildTask(final AntBuildTargetBase target, final AntDomElement task) {
    myTarget = target;
    myName = task.getXmlElementName();
    final DomTarget domTarget = DomTarget.getTarget(task);
    if (domTarget != null) {
      myOffset = domTarget.getTextOffset();
    }
    else {
      myOffset = task.getXmlTag().getTextOffset();
    }
  }

  public @NlsSafe String getName() {
    return myName;
  }

  public @Nullable Navigatable getOpenFileDescriptor() {
    final VirtualFile vFile = myTarget.getContainingFile();
    return vFile != null ? PsiNavigationSupport.getInstance()
                                               .createNavigatable(myTarget.getProject(), vFile, myOffset) : null;
  }
}
