package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.TimeCommitParents;
import com.intellij.vcs.log.parser.CommitParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogJoinerTest {

  @Test
  public void simpleTest() {
    String[] INITIAL = {"4|-a2|-a1", "3|-b1|-a", "2|-a1|-a", "1|-a|-"};
    List<TimeCommitParents> fullLog = CommitParser.log(INITIAL);
    List<? extends TimeCommitParents> firstBlock = CommitParser.log("5|-f|-b1", "6|-e|-a2");
    Collection<VcsRef> refs = Arrays.asList(ref("master", "e"), ref("release", "f"));

    List<TimeCommitParents> expected = CommitParser.log(ArrayUtil.mergeArrays(new String[]{"6|-e|-a2", "5|-f|-b1"}, INITIAL));

    List<? extends TimeCommitParents> result = new VcsLogJoiner().addCommits(fullLog, refs, firstBlock, refs).getFirst();

    assertEquals(expected, result);
  }

  private static VcsRef ref(String name, String hash) {
    return new VcsRef(Hash.build(hash), name, VcsRef.RefType.LOCAL_BRANCH, new StubVirtualFile());
  }
}
