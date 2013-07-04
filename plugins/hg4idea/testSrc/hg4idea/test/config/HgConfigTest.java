package hg4idea.test.config;

import com.intellij.openapi.util.io.FileUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.io.IOException;

import static com.intellij.dvcs.test.Executor.cd;

/**
 * @author Nadya Zabrodina
 */
public class HgConfigTest extends HgPlatformTest {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    prepareSecondRepository();
  }

  public void testDefaultPathInClonedRepo() {
    cd(myChildRepo);
    final String defaultPath = HgUtil.getRepositoryDefaultPath(myProject, myChildRepo);
    assertNotNull(defaultPath);
    assertEquals(myRepository.getCanonicalPath(),
                 FileUtil.toSystemIndependentName(defaultPath));
  }

  public void testPushPathInClonedRepo() {
    cd(myChildRepo);
    String pushPath = "somePath";
    appendToHgrc(myChildRepo, "\n[paths]\n" +
                              "default-push=" + pushPath);
    updateRepoConfig(myProject, myChildRepo);
    final String defaultPushPath = HgUtil.getRepositoryDefaultPushPath(myProject, myChildRepo);
    assertNotNull(defaultPushPath);
    assertEquals(FileUtil.toSystemIndependentName(myChildRepo.getCanonicalPath() + "/" + pushPath),
                 FileUtil.toSystemIndependentName(defaultPushPath));
  }

  public void testPushPathWithoutAppropriateConfig() {
    cd(myChildRepo);
    final String defaultPushPath = HgUtil.getRepositoryDefaultPushPath(myProject, myChildRepo);
    assertNotNull(defaultPushPath);
    assertEquals(myRepository.getCanonicalPath(),
                 FileUtil.toSystemIndependentName(defaultPushPath));
  }

  public void testLargeExtensionInClonedRepo() {
    cd(myChildRepo);
    File hgrc = new File(new File(myChildRepo.getPath(), ".hg"), "hgrc");
    assert hgrc.exists();
    try {
      FileUtil.appendToFile(hgrc, "\n[extensions]\n" +
                                  "largefiles =");
    }
    catch (IOException e) {
      e.printStackTrace();
      fail("Can not update hgrc file.");
    }
    updateRepoConfig(myProject, myChildRepo);
    assertNotNull(HgUtil.getRepositoryNamedConfig(myProject, myChildRepo, "extensions", "largefiles"));
  }
}
