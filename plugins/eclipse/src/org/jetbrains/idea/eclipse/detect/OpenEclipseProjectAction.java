// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.detect;

import com.intellij.openapi.wm.impl.welcomeScreen.OpenAlienProjectAction;

@SuppressWarnings("ComponentNotRegistered")
public class OpenEclipseProjectAction extends OpenAlienProjectAction {

  public OpenEclipseProjectAction() {
    super(new EclipseProjectDetector());
  }

  @Override
  protected void projectOpened() {
    EclipseProjectDetectorUsagesCollector.logProjectOpened(true);
  }
}
