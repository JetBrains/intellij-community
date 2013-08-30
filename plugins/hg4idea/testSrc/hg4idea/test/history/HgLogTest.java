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
    if (!SystemInfo.isWindows) {
      myVcs.getGlobalSettings().setRunViaBash(true);
      try {
        parseCopied("f1.txt");
      }
      finally {
        myVcs.getGlobalSettings().setRunViaBash(false);
      }
    }
  }

  public void testParseCopiedWithBraces() throws HgCommandException {
    parseCopied("(f.txt)");
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
