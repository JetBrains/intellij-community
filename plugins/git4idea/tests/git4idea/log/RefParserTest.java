package git4idea.log;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.util.NotNullFunction;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.VcsRefImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class RefParserTest {

  public String toStr(VcsRef ref) {
    return String.format("%s TAG %s", ref.getCommitHash().asString(), ref.getName());
  }

  public void runTest(String inputStr, String outStr) {
    List<VcsRef> refs = new RefParser(new TestLogObjectsFactory()).parseCommitRefs(inputStr, NullVirtualFile.INSTANCE);
    StringBuilder s = new StringBuilder();
    for (VcsRef ref : refs) {
      if (s.length() > 0) {
        s.append("\n");
      }
      s.append(toStr(ref));
    }
    assertEquals(outStr, s.toString());
  }

  @Test
  public void tagTest() {
    runTest("22762ebf7203f6a2888425a3207d2ddc63085dd7 (tag: refs/tags/v3.6-rc1, refs/heads/br)",
            "22762ebf7203f6a2888425a3207d2ddc63085dd7 TAG v3.6-rc1");
  }

  @Test
  public void severalRefsTest() {
    runTest("f85125c (refs/tags/v3.6-rc1, HEAD)", "f85125c TAG v3.6-rc1");
  }

  @Test
  public void severalRefsTest2() {
    runTest("ed7a0d14da090ea256d68a06c6f8dd7311de192e (refs/tags/category/v3.6-rc1, HEAD, refs/remotes/origin/graph_fix)",
            "ed7a0d14da090ea256d68a06c6f8dd7311de192e TAG category/v3.6-rc1");
  }

  @Test
  public void severalRefsTest3() {
    runTest("ed7a0d14da090ea256d68a06c6f8dd7311de192e (tag: refs/tags/web/130.1599, tag: refs/tags/ruby/130.1597, " +
            "tag: refs/tags/py/130.1598, " +
            "tag: refs/tags/php/130.1596, tag: refs/tags/idea/130.1601, tag: refs/tags/app/130.1600)",
            "ed7a0d14da090ea256d68a06c6f8dd7311de192e TAG web/130.1599\n" +
            "ed7a0d14da090ea256d68a06c6f8dd7311de192e TAG ruby/130.1597\n" +
            "ed7a0d14da090ea256d68a06c6f8dd7311de192e TAG py/130.1598\n" +
            "ed7a0d14da090ea256d68a06c6f8dd7311de192e TAG php/130.1596\n" +
            "ed7a0d14da090ea256d68a06c6f8dd7311de192e TAG idea/130.1601\n" +
            "ed7a0d14da090ea256d68a06c6f8dd7311de192e TAG app/130.1600"
    );
  }


  private class TestLogObjectsFactory implements VcsLogObjectsFactory {
    @NotNull
    @Override
    public Hash createHash(@NotNull String stringHash) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public VcsCommit createCommit(@NotNull Hash hash, @NotNull List<Hash> parents) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public TimedVcsCommit createTimedCommit(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public VcsShortCommitDetails createShortDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long timeStamp, VirtualFile root,
                                                    @NotNull String subject, @NotNull String authorName, String authorEmail) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public VcsFullCommitDetails createFullDetails(@NotNull Hash hash, @NotNull List<Hash> parents, long time, VirtualFile root,
                                                  @NotNull String subject, @NotNull String authorName, @NotNull String authorEmail,
                                                  @NotNull String message, @NotNull String committerName, @NotNull String committerEmail,
                                                  long authorTime, @NotNull List<Change> changes,
                                                  @NotNull ContentRevisionFactory contentRevisionFactory) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public VcsUser createUser(@NotNull String name, @NotNull String email) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public VcsRef createRef(@NotNull Hash commitHash, @NotNull String name, @NotNull VcsRefType type, @NotNull VirtualFile root) {
      return new VcsRefImpl(new NotNullFunction<Hash, Integer>() {
        @NotNull
        @Override
        public Integer fun(Hash dom) {
          return Integer.parseInt(dom.asString().substring(0, Math.min(4, dom.asString().length())), 16);
        }
      }, commitHash, name, type, root);
    }
  }
}
