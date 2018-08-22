// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.runtime.deployment;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class DeploymentStatus {

  public static final DeploymentStatus DEPLOYED = new DeploymentStatus(AllIcons.RunConfigurations.TestPassed, "Deployed", false);
  public static final DeploymentStatus NOT_DEPLOYED = new DeploymentStatus(AllIcons.RunConfigurations.TestIgnored, "Not deployed", false);
  public static final DeploymentStatus DEPLOYING = new DeploymentStatus(AllIcons.Process.Step_4, "Deploying", true);
  public static final DeploymentStatus UNDEPLOYING = new DeploymentStatus(AllIcons.Process.Step_4, "Undeploying", true);

  private final Icon myIcon;
  private final String myPresentableText;
  private final boolean myTransition;

  public DeploymentStatus(Icon icon, @NotNull String presentableText, boolean transition) {
    myIcon = icon;
    myPresentableText = presentableText;
    myTransition = transition;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public String getPresentableText() {
    return myPresentableText;
  }

  public boolean isTransition() {
    return myTransition;
  }
}
