/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.idea.devkit.module;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.idea.devkit.sandbox.Sandbox;
import org.jetbrains.idea.devkit.sandbox.SandboxManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import   private Sandbox mySandbox;
com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.options.Co  }

  public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
    super.setupRootModel(rootModel);
    ModuleSandboxManager.getInstance(rootModel.getModule()).setSandbox(getSandbox(), rootModel);
  }

  public void setSandbox(Sandbox sandbox) {
    mySandbox = sandbox;
  }

  public Sandbox getSandbox() {
    return mySandbox;
nfigurat