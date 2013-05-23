package org.hanuna.gitalk.git.reader;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.git.reader.util.GitException;
import org.hanuna.gitalk.git.reader.util.GitProcessFactory;
import org.hanuna.gitalk.git.reader.util.ProcessOutputReader;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.log.parser.CommitParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class FullLogCommitParentsReader {
    private final Executor<String> statusUpdater;

    public FullLogCommitParentsReader(final Executor<String> statusUpdater) {
        this.statusUpdater = statusUpdater;
    }

    public List<CommitParents> readAllCommitParents() throws IOException, GitException {
        final List<CommitParents> commitParentsList = new ArrayList<CommitParents>();
        Executor<Integer> progressUpdater = new Executor<Integer>() {
            @Override
            public void execute(Integer key) {
                if (key % 100 == 0) {
                    statusUpdater.execute("Read " + key + " commits");
                }
            }
        };
        final MyTimer gitThink = new MyTimer("gitThink");
        final MyTimer readTimer = new MyTimer("read commit parents");
        ProcessOutputReader outputReader = new ProcessOutputReader(progressUpdater, new Executor<String>() {
            private boolean wasReadFirstLine = false;
            @Override
            public void execute(String key) {
                if (!wasReadFirstLine) {
                    wasReadFirstLine = true;
                    gitThink.print();
                    readTimer.clear();
                }
                CommitParents commitParents = CommitParser.parseCommitParents(key);
                commitParentsList.add(commitParents);
            }
        });
        statusUpdater.execute("Begin load git repository");
        outputReader.startRead(GitProcessFactory.allLog());
        readTimer.print();
        return commitParentsList;
    }

}
