package org.hanuna.gitalk.git.reader;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.git.reader.util.ProcessOutputReader;
import org.hanuna.gitalk.log.commit.parents.TimestampCommitParents;
import org.hanuna.gitalk.log.parser.CommitParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CrazyGitLogTest {
    public static final int DAY_INTERVAL = 5000000;
    private final static String FORMAT = " --format=%ct|-%h|-%p";

    public static Process gitLogPart(long dayStart) throws IOException {
        String request = "git log --all --before=" + (dayStart + DAY_INTERVAL) + " --since=" + dayStart +"" + FORMAT +
                " --full-history --sparse";
        System.out.println(request);
        return Runtime.getRuntime().exec(request);
    }

    public static Process gitLogFull() throws IOException {
        String request = "git log --all" + FORMAT;
        return Runtime.getRuntime().exec(request);
    }

    public static List<TimestampCommitParents> getAll() throws IOException, GitException {
        final List<TimestampCommitParents> commitParentsList = new ArrayList<TimestampCommitParents>();
        ProcessOutputReader reader = new ProcessOutputReader(new Executor<String>() {
            @Override
            public void execute(String key) {
                commitParentsList.add(CommitParser.parseTimestampParentHashes(key));
            }
        });
        reader.startRead(gitLogFull());
        return commitParentsList;
    }

    public static List<TimestampCommitParents> getPart(long dayStart,
                                                       final List<TimestampCommitParents> commitParentsList)
            throws IOException, GitException {
        ProcessOutputReader reader = new ProcessOutputReader(new Executor<String>() {
            @Override
            public void execute(String key) {
                commitParentsList.add(CommitParser.parseTimestampParentHashes(key));
            }
        });
        reader.startRead(gitLogPart(dayStart));
        return commitParentsList;
    }

    public static List<TimestampCommitParents> getAllPart() throws IOException, GitException {
        List<TimestampCommitParents> commitParentsList = new ArrayList<TimestampCommitParents>();
        int k = 0;

        MyTimer timer = new MyTimer("part");
        long timestamp = 1390000000;
        for (int i = 0; i < 10000; i++) {
            if (k > 30) {
                break;
            }
            int oldSize = commitParentsList.size();
            getPart(timestamp - i * DAY_INTERVAL, commitParentsList);
            if (commitParentsList.size() == oldSize) {
                k++;
            } else {
                k = 0;
            }
            timer.print();
            timer.clear();
        }
        return commitParentsList;
    }

    public static void main(String[] args) throws IOException, GitException {
        MyTimer timer = new MyTimer("all");
        List<TimestampCommitParents> list1 = getAll();
        timer.print();

        timer.clear("all part");
        List<TimestampCommitParents> list2 = getAllPart();
        timer.print();

        System.out.println(list1.size() + ":" + list2.size());

    }

}
