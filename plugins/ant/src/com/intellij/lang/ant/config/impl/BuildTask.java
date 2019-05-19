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

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ant.config.AntBuildTargetBase;
import com.intellij.lang.ant.dom.AntDomElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.xml.DomTarget;
import org.jetbrains.annotations.Nullable;

public final class BuildTask {
  public static final BuildTask[] EMPTY_ARRAY = new BuildTask[0];
  private final AntBuildTargetBase myTarget;
  private final String myName;
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

  public String getName() {
    return myName;
  }

  @Nullable
  public Navigatable getOpenFileDescriptor() {
    final VirtualFile vFile = myTarget.getContainingFile();
    return vFile != null ? PsiNavigationSupport.getInstance()
                                               .createNavigatable(myTarget.getProject(), vFile, myOffset) : null;
  }
}
