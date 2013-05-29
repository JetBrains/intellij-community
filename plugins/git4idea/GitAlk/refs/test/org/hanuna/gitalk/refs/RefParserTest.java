package org.hanuna.gitalk.refs;

import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class RefParserTest {

    public String toStr(Ref ref) {
        StringBuilder s = new StringBuilder();
        s.append(ref.getCommitHash().toStrHash()).append(" ");
        s.append(ref.getType()).append(" ");
        s.append(ref.getName()).append(":").append(ref.getShortName());
        return s.toString();
    }

    public void runTest(String inputStr, String outStr) {
        List<Ref> refs = RefParser.parseCommitRefs(inputStr);
        StringBuilder s = new StringBuilder();
        for (Ref ref : refs) {
            if (s.length() > 0) {
                s.append("\n");
            }
            s.append(toStr(ref));
        }
        assertEquals(outStr, s.toString());
    }

    @Test
    public void headTest() throws Exception {
        runTest("c2f2356 (HEAD)", "c2f2356 LOCAL_BRANCH HEAD:HEAD");
    }

    @Test
    public void stashTest() {
        runTest("7fe7245 (refs/stash)", "7fe7245 STASH stash:stash");
    }

    @Test
    public void remoteBranch() {
        runTest("2d3cc9d (refs/remotes/origin/HEAD)", "2d3cc9d REMOTE_BRANCH origin/HEAD:HEAD");
    }

    @Test
    public void localBranch() {
        runTest("c2f2356 (refs/heads/master)", "c2f2356 LOCAL_BRANCH master:master");
    }

    @Test
    public void tagTest() {
        runTest("f85125c (refs/tags/v3.6-rc1)", "f85125c TAG v3.6-rc1:v3.6-rc1");
    }

    @Test
    public void severalRefsTest() {
        runTest("f85125c (refs/tags/v3.6-rc1, HEAD)",
                "f85125c TAG v3.6-rc1:v3.6-rc1\n" +
                "f85125c LOCAL_BRANCH HEAD:HEAD"
        );
    }

    @Test
    public void severalRefsTest2() {
        runTest("f85125c (refs/tags/v3.6-rc1, HEAD, refs/remotes/origin/graph_fix)",
                "f85125c TAG v3.6-rc1:v3.6-rc1\n" +
                "f85125c LOCAL_BRANCH HEAD:HEAD\n" +
                "f85125c REMOTE_BRANCH origin/graph_fix:graph_fix"
        );
    }
}
