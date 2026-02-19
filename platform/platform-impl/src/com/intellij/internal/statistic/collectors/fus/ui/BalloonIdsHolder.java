// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.ui.BalloonImpl;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Class that provides constant list of possible balloon ids.
 * If extension is registered in the platform or in a plugin built with IntelliJ Ultimate,
 * these ids will be registered in statistic metadata repository automatically.
 * <p>
 * Otherwise, create a YT issue in FUS project.
 */
@ApiStatus.Internal
public interface BalloonIdsHolder {
  ExtensionPointName<BalloonIdsHolder> EP_NAME = ExtensionPointName.create("com.intellij.statistics.balloonIdsHolder");

  /**
   * List of balloon ids which should be recorded in feature usage statistics.
   *
   * @see BalloonImpl#myId
   */
  List<String> getBalloonIds();
}
