package hg4idea.test.config;

import com.intellij.openapi.util.io.FileUtil;
import hg4idea.test.HgPlatformTest;
import org.zmlx.hg4idea.repo.HgRepository;
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
    HgRepository hgRepository = HgUtil.getRepositoryManager(myProject).getRepositoryForRoot(myChildRepo);
    assertNotNull(hgRepository);
    hgRepository.getRepositoryConfig().update(myProject, null);
    assertNotNull(HgUtil.getRepositoryNamedConfig(myProject, myChildRepo, "extensions.largefiles"));
  }
}
