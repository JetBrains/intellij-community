// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;

@TestApplication
public class GrLibrarySourceHighlightingTest {
  private static final String TEST_DATA_PATH = TestUtils.getAbsoluteTestDataPath() + "highlighting/librarySources";
  private static final LightProjectDescriptor DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      final String absoluteBasePath = TEST_DATA_PATH;
      VfsRootAccess.allowRootAccess(module, absoluteBasePath);
      final VirtualFile lib = JarFileSystem.getInstance().refreshAndFindFileByPath(absoluteBasePath + "/some-library.jar!/");
      final VirtualFile src = StandardFileSystems.local().refreshAndFindFileByPath(absoluteBasePath + "/src/");

      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary("some-library").getModifiableModel();
      modifiableModel.addRoot(lib, OrderRootType.CLASSES);
      modifiableModel.addRoot(src, OrderRootType.SOURCES);
      modifiableModel.commit();
    }
  };
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture, TEST_DATA_PATH);

  @Test
  public void testNoErrorsTraitHighlighting() {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    PsiFile psiFile = ReadAction.computeBlocking(() -> {
      PsiClass clazz =
        JavaPsiFacade.getInstance(myFixture.getProject()).findClass("somepackage.CC", GlobalSearchScope.moduleWithLibrariesScope(myFixture.getModule()));
      return (DefaultGroovyMethods.asType(clazz, ClsClassImpl.class)).getSourceMirrorClass().getContainingFile();
    });
    myFixture.configureFromExistingVirtualFile(psiFile.getVirtualFile());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.testHighlighting();
    });
  }
}
