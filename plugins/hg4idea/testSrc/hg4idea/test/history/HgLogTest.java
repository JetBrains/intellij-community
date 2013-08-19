package hg4idea.test.history;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgFileRevision;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgLogCommand;
import org.zmlx.hg4idea.execution.HgCommandException;

import java.util.List;
import java.util.Map;

import static com.intellij.dvcs.test.Executor.cd;
import static com.intellij.dvcs.test.Executor.touch;
import static hg4idea.test.HgExecutor.hg;

/**
 * @author Nadya Zabrodina
 */
public class HgLogTest extends HgPlatformTest {
  private HgVcs myVcs;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myVcs = HgVcs.getInstance(myProject);
    assert myVcs != null;
  }

  public void testParseCopiedWithoutBraces() throws HgCommandException {
    parseCopied("f.txt");
  }

  public void testParseCopiedWithBraces() throws HgCommandException {
    parseCopied("(f.txt)");
  }

  public void testLogCommandViaBash() throws HgCommandException {
    if (SystemInfo.isWindows) {
      return;
    }
    myVcs.getGlobalSettings().setRunViaBash(true);
    parseCopied("f.txt");
  }

  public void testParseFileCopiesWithWhitespaces() {
    Map<String, String> filesMap = HgLogCommand.parseCopiesFileList("/a/b c/d.txt (a/b a/d.txt)\u0001/a/b c/(d).txt (/a/b c/(f).txt)");
    assertTrue(filesMap.containsKey("a/b a/d.txt"));
    assertTrue(filesMap.containsKey("/a/b c/(f).txt"));
    assertTrue(filesMap.containsValue("/a/b c/d.txt"));
    assertTrue(filesMap.containsValue("/a/b c/(d).txt"));
  }

  private void parseCopied(@NotNull String sourceFileName) throws HgCommandException {
    cd(myRepository);
    String copiedFileName = "copy".concat(sourceFileName);
    touch(sourceFileName);
    myRepository.refresh(false, true);
    hg("add " + sourceFileName);
    hg("commit -m a ");
    hg("cp " + sourceFileName + " " + copiedFileName);
    myRepository.refresh(false, true);
    hg("commit -m a ");
    HgLogCommand logCommand = new HgLogCommand(myProject);
    logCommand.setFollowCopies(false);
    VirtualFile copiedFile = myRepository.findChild(copiedFileName);
    assert copiedFile != null;
    final HgFile hgFile = new HgFile(myRepository, VfsUtilCore.virtualToIoFile(copiedFile));
    List<HgFileRevision> revisions = logCommand.execute(hgFile, 1, true);
    HgFileRevision rev = revisions.get(0);
    assertTrue(!rev.getAddedFiles().isEmpty());
  }
}
