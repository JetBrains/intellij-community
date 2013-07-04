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
    assertEquals(myRepository.getPath(),
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
    assertEquals(FileUtil.toSystemIndependentName(myChildRepo.getPath() + "/" + pushPath),
                 FileUtil.toSystemIndependentName(defaultPushPath));
  }

  public void testPushPathWithoutAppropriateConfig() {
    cd(myChildRepo);
    final String defaultPushPath = HgUtil.getRepositoryDefaultPushPath(myProject, myChildRepo);
    assertNotNull(defaultPushPath);
    assertEquals(myRepository.getPath(),
                 FileUtil.toSystemIndependentName(defaultPushPath));
  }

  public void testLargeExtensionInClonedRepo() throws IOException {
    cd(myChildRepo);
    File hgrc = new File(new File(myChildRepo.getPath(), ".hg"), "hgrc");
    assert hgrc.exists();
    FileUtil.appendToFile(hgrc, "\n[extensions]\n" +
                                "largefiles =");
    updateRepoConfig(myProject, myChildRepo);
    assertNotNull(HgUtil.getConfig(myProject, myChildRepo, "extensions", "largefiles"));
  }
}
