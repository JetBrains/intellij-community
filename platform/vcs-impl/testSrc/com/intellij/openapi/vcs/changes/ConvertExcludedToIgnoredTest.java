package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ConvertExcludedToIgnoredTest extends PlatformTestCase {
  private VirtualFile myContentRoot;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myContentRoot = getVirtualFile(createTempDirectory());
    PsiTestUtil.addContentRoot(myModule, myContentRoot);
  }

  public void testExcludedFolder() throws IOException {
    VirtualFile excluded = createChildDirectory(myContentRoot, "exc");
    PsiTestUtil.addExcludedRoot(myModule, excluded);
    getChangeListManager().convertExcludedToIgnored();
    assertFalse(getChangeListManager().isIgnoredFile(myContentRoot));
    assertTrue(getChangeListManager().isIgnoredFile(excluded));
    assertIgnored(excluded);
  }

  public void testModuleOutput() throws IOException {
    VirtualFile output = createChildDirectory(myContentRoot, "out");
    PsiTestUtil.setCompilerOutputPath(myModule, output.getUrl(), false);
    getChangeListManager().convertExcludedToIgnored();
    assertFalse(getChangeListManager().isIgnoredFile(myContentRoot));
    assertTrue(getChangeListManager().isIgnoredFile(output));
    assertIgnored(output);
  }

  public void testProjectOutput() throws IOException {
    VirtualFile output = getVirtualFile(createTempDir("projectOutput"));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(output.getUrl());
    getChangeListManager().convertExcludedToIgnored();
    assertTrue(getChangeListManager().isIgnoredFile(output));
    assertIgnored(output);
  }

  public void testModuleOutputUnderProjectOutput() throws IOException {
    VirtualFile output = getVirtualFile(createTempDir("projectOutput"));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(output.getUrl());
    VirtualFile moduleOutput = createChildDirectory(output, "module");
    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutput.getUrl(), false);
    getChangeListManager().convertExcludedToIgnored();
    assertTrue(getChangeListManager().isIgnoredFile(output));
    assertTrue(getChangeListManager().isIgnoredFile(moduleOutput));
    assertIgnored(output);
  }

  public void testModuleOutputUnderExcluded() throws IOException {
    VirtualFile excluded = createChildDirectory(myContentRoot, "target");
    PsiTestUtil.addExcludedRoot(myModule, excluded);
    VirtualFile moduleOutput = createChildDirectory(excluded, "classes");
    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutput.getUrl(), false);
    getChangeListManager().convertExcludedToIgnored();
    assertTrue(getChangeListManager().isIgnoredFile(excluded));
    assertTrue(getChangeListManager().isIgnoredFile(moduleOutput));
    assertIgnored(excluded);
  }

  private void assertIgnored(VirtualFile... ignoredDirs) {
    assertIgnoredDirectories(getProject(), ignoredDirs);
  }

  public static void assertIgnoredDirectories(final Project project, VirtualFile... ignoredDirs) {
    List<String> expectedIgnoredPaths = new ArrayList<String>();
    for (VirtualFile dir : ignoredDirs) {
      expectedIgnoredPaths.add(dir.getPath() + "/");
    }
    List<String> actualIgnoredPaths = new ArrayList<String>();
    for (IgnoredFileBean fileBean : ChangeListManagerImpl.getInstanceImpl(project).getFilesToIgnore()) {
      assertEquals("Unexpected ignore: " + fileBean, IgnoreSettingsType.UNDER_DIR, fileBean.getType());
      actualIgnoredPaths.add(fileBean.getPath());
    }
    assertSameElements(expectedIgnoredPaths, actualIgnoredPaths);
  }

  private ChangeListManagerImpl getChangeListManager() {
    return ChangeListManagerImpl.getInstanceImpl(getProject());
  }
}
