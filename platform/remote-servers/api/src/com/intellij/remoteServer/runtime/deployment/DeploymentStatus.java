// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.runtime.deployment;

import com.intellij.icons.AllIcons;
import com.intellij.remoteServer.CloudBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

public class DeploymentStatus {

  public static final DeploymentStatus DEPLOYED = new DeploymentStatus(AllIcons.RunConfigurations.TestPassed,
                                                                       CloudBundle.messagePointer("DeploymentStatus.deployed"),
                                                                       false);

  public static final DeploymentStatus NOT_DEPLOYED = new DeploymentStatus(AllIcons.RunConfigurations.TestIgnored,
                                                                           CloudBundle.messagePointer("DeploymentStatus.not.deployed"),
                                                                           false);

  public static final DeploymentStatus DEPLOYING = new DeploymentStatus(AllIcons.Process.Step_4,
                                                                        CloudBundle.messagePointer("DeploymentStatus.deploying"),
                                                                        true);

  public static final DeploymentStatus UNDEPLOYING = new DeploymentStatus(AllIcons.Process.Step_4,
                                                                          CloudBundle.messagePointer("DeploymentStatus.undeploying"),
                                                                          true);

  private final Icon myIcon;
  private final Supplier<@Nls String> myPresentableText;
  private final boolean myTransition;

  public DeploymentStatus(Icon icon, @NotNull @Nls String presentableText, boolean transition) {
    this(icon, () -> presentableText, transition);
  }

  public DeploymentStatus(Icon icon, @NotNull Supplier<@Nls String> presentableText, boolean transition) {
    myIcon = icon;
    myPresentableText = presentableText;
    myTransition = transition;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  @Nls
  public String getPresentableText() {
    return myPresentableText.get();
  }

  public boolean isTransition() {
    return myTransition;
  }
}
