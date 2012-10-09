/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.designSurface;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTreeChangeEvent;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * @author Alexander Lobas
 */
public class ExternalPSIChangeListener extends com.intellij.designer.designSurface.ExternalPSIChangeListener {
  private VirtualFile[] myResourceDepends;

  public ExternalPSIChangeListener(DesignerEditorPanel designer, PsiFile file, int delayMillis, Runnable runnable) {
    super(designer, file, delayMillis, runnable);
  }

  @Override
  public void deactivate() {
    super.deactivate();

    if (!myDesigner.isProjectClosed()) {
      AndroidFacet facet = AndroidFacet.getInstance(myDesigner.getModule());
      myResourceDepends = facet == null ? null : facet.getLocalResourceManager().getAllResourceDirs();
    }
  }

  @Override
  protected void updatePsi(PsiTreeChangeEvent event) {
    boolean runState = myRunState;
    super.updatePsi(event);

    if (!runState && myResourceDepends != null && !myUpdateRenderer) {
      PsiFile psiFile = event.getFile();
      if (psiFile == null) {
        return;
      }
      VirtualFile file = psiFile.getVirtualFile();
      if (file == null) {
        return;
      }
      for (VirtualFile resourceDir : myResourceDepends) {
        if (VfsUtilCore.isAncestor(resourceDir, file, false)) {
          myUpdateRenderer = true;
          break;
        }
      }
    }
  }
}