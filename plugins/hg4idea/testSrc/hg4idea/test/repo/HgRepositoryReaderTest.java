package hg4idea.test.repo;

import com.intellij.dvcs.test.TestRepositoryUtil;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.io.FileUtil;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepositoryReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Nadya Zabrodina
 */
public class HgRepositoryReaderTest extends HgPlatformTest {


  @NotNull private HgRepositoryReader myRepositoryReader;
  @NotNull private File myHgDir;
  @NotNull private Collection<String> myBranches;

  public void setUp() throws Exception {
    super.setUp();
    myHgDir = new File(myRepository.getPath(), ".hg");
    assertTrue(myHgDir.exists());
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("hg4idea"));

    String pathToHg = "testData/repo/dot_hg";
    File testHgDir = new File(pluginRoot, FileUtil.toSystemDependentName(pathToHg));

    File cacheDir = new File(testHgDir, "cache");
    File testBranchFile = new File(testHgDir, "branch");
    FileUtil.copyDir(cacheDir, new File(myHgDir, "cache"));
    FileUtil.copy(testBranchFile, new File(myHgDir, "branch"));

    myRepositoryReader = new HgRepositoryReader(myHgDir);
    myBranches = readBranches();
  }


  public void testHEAD() {
    assertEquals("25e44c95b2612e3cdf29a704dabf82c77066cb67", myRepositoryReader.readCurrentRevision());
  }

  public void testCurrentBranch() {
    String currentBranch = myRepositoryReader.readCurrentBranch();
    assertEquals(currentBranch, "firstBranch");
  }

  public void testBranches() {
    Collection<String> branches = myRepositoryReader.readBranches();
    TestRepositoryUtil.assertEqualCollections(branches, myBranches);
  }

  @NotNull
  private Collection<String> readBranches() throws IOException {
    final Collection<String> branches = new ArrayList<String>();
    String branchesName = "branchheads-served";
    final File branchHeads = new File(new File(myHgDir, "cache"), branchesName);
    String[] branchesWithHashes = FileUtil.loadFile(branchHeads).split("\n");
    for (int i = 1; i < branchesWithHashes.length; ++i) {
      String ref = branchesWithHashes[i];
      String[] refAndName = ref.split(" ");
      String name = refAndName[1];

      if (!branches.contains(name)) {
        branches.add(name);
      }
    }
    return branches;
  }
}
