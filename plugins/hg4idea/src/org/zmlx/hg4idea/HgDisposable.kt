// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
public final class HgDisposable implements Disposable {
  @Override
  public void dispose() {
  }

  public static HgDisposable getInstance(Project project) {
    return project.getService(HgDisposable.class);
  }
}
