package git4idea.log;

import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.vcs.log.VcsRef;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class RefParserTest {

  public String toStr(VcsRef ref) {
    return String.format("%s %s %s", ref.getCommitHash().asString(), ref.getType(), ref.getName());
  }

  public void runTest(String inputStr, String outStr) {
    List<VcsRef> refs = RefParser.parseCommitRefs(inputStr, NullVirtualFile.INSTANCE);
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


}
