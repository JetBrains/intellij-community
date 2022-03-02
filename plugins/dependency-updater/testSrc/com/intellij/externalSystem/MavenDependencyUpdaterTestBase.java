package com.intellij.externalSystem;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlTag;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.VfsTestUtil;
import junit.framework.AssertionFailedError;
import com.intellij.maven.testFramework.MavenImportingTestCase;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

abstract public class MavenDependencyUpdaterTestBase extends MavenImportingTestCase {
  protected File myTestDataDir;
  protected File myProjectDataDir;
  protected File myExpectedDataDir;
  protected DependencyModifierService myModifierService;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTestDataDir = PathManagerEx.findFileUnderCommunityHome("plugins/dependency-updater/testData/maven");
    assertTrue(myTestDataDir.isDirectory());
    myProjectDataDir = new File(new File(myTestDataDir, "projects"), getTestName(true));
    myExpectedDataDir = new File(new File(myTestDataDir, "expected"), getTestName(true));
    myModifierService = DependencyModifierService.getInstance(myProject);
    prepareAndImport();
  }

  protected void prepareAndImport() throws IOException {
    createProjectPom("");
    FileUtil.copyDir(myProjectDataDir, myProjectRoot.toNioPath().toFile());
    myProjectRoot.refresh(false, true);
    importProjectWithErrors();
  }

  protected XmlTag findDependencyTag(String group, String artifact, String version) {
    PsiFile pom = PsiUtilCore.getPsiFile(myProject, myProjectPom);
    return findDependencyTag(group, artifact, version, pom);
  }

  protected XmlTag findDependencyTag(String group, String artifact, String version, PsiFile pom) {
    MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(pom, MavenDomProjectModel.class);
    for (MavenDomDependency dependency : model.getDependencies().getDependencies()) {
      if (dependency.getGroupId().getStringValue().equals(group) &&
          dependency.getArtifactId().getStringValue().equals(artifact) &&
          dependency.getVersion().getStringValue().equals(version)) {
        return dependency.getXmlTag();
      }
    }
    return null;
  }

  protected void assertFilesAsExpected() throws IOException {
    assertTrue(new File(myExpectedDataDir, "pom.xml").isFile());

    Files.walkFileTree(myExpectedDataDir.toPath(), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        File expectedFile = file.toFile();
        Path relativePath = myExpectedDataDir.toPath().relativize(file);
        VirtualFile actual = myProjectRoot.findFileByRelativePath(FileUtil.normalize(relativePath.toString()));
        if (actual == null) {
          fail("File " + file + " not found in actual dir");
        }
        String value = new String(actual.contentsToByteArray(), actual.getCharset());
        assertFilesAreIdenticalLineByLineIgnoringIndent(expectedFile.getPath(), value);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private static void assertFilesAreIdenticalLineByLineIgnoringIndent(String expectedFilePath, String actualText) {
    String expectedText;
    try {
      if (OVERWRITE_TESTDATA) {
        VfsTestUtil.overwriteTestData(expectedFilePath, actualText);
        //noinspection UseOfSystemOutOrSystemErr
        System.out.println("File " + expectedFilePath + " created.");
      }
      expectedText = FileUtil.loadFile(new File(expectedFilePath), StandardCharsets.UTF_8);
    }
    catch (FileNotFoundException e) {
      VfsTestUtil.overwriteTestData(expectedFilePath, actualText);
      throw new AssertionFailedError("No output text found. File " + expectedFilePath + " created.");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    var expectedLines = Arrays.stream(StringUtil.splitByLines(expectedText.trim(), true))
      .map(s -> s.stripLeading())
      .toArray();
    var actualLines = Arrays.stream(StringUtil.splitByLines(actualText.trim(), true))
      .map(s -> s.stripLeading())
      .toArray();

    if (!Arrays.equals(expectedLines, actualLines)) {
      throw new FileComparisonFailure(null, StringUtil.convertLineSeparators(expectedText.trim()),
                                      StringUtil.convertLineSeparators(actualText.trim()), expectedFilePath);
    }
  }
}
