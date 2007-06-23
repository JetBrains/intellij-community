/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.testFramework.ResolveTestCase;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @uthor ven
 */
public abstract class GroovyResolveTestCase extends ResolveTestCase {

  protected abstract String getTestDataPath();

  protected void setUp() throws Exception {
    super.setUp();

    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(getModule()).getModifiableModel();
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(getTestDataPath());
    assertNotNull(root);
    ContentEntry contentEntry = rootModel.addContentEntry(root);
    rootModel.setJdk(JavaSdk.getInstance().createJdk("java sdk", TestUtils.getMockJdkHome(), false));
    final VirtualFile sourceRoot = root.findChild(getTestName(true));
    assertNotNull(sourceRoot);
    contentEntry.addSourceFolder(sourceRoot, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });

    GroovyPsiManager.getInstance(getProject()).buildGDK();
  }
}
