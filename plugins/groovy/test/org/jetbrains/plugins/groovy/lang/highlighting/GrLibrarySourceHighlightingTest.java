// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GrLibrarySourceHighlightingTest extends GrHighlightingTestBase {
  public void testNoErrorsTraitHighlighting() {
    PsiClass clazz =
      JavaPsiFacade.getInstance(getProject()).findClass("somepackage.CC", GlobalSearchScope.moduleWithLibrariesScope(getModule()));
    PsiFile psiFile = (DefaultGroovyMethods.asType(clazz, ClsClassImpl.class)).getSourceMirrorClass().getContainingFile();
    myFixture.configureFromExistingVirtualFile(psiFile.getVirtualFile());
    myFixture.testHighlighting();
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor() {
      @Override
      public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
        final String absoluteBasePath = TestUtils.getAbsoluteTestDataPath() + getBasePath();
        final VirtualFile lib = JarFileSystem.getInstance().refreshAndFindFileByPath(absoluteBasePath + "/some-library.jar!/");
        final VirtualFile src = LocalFileSystem.getInstance().refreshAndFindFileByPath(absoluteBasePath + "/src/");

        final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("some-library").getModifiableModel();
        modifiableModel.addRoot(lib, OrderRootType.CLASSES);
        modifiableModel.addRoot(src, OrderRootType.SOURCES);
        modifiableModel.commit();
      }
    };
  }

  @Override
  public final String getBasePath() {
    return "highlighting/librarySources";
  }
}
