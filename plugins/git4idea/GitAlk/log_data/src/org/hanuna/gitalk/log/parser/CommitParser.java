package org.hanuna.gitalk.log.parser;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.log.commit.CommitData;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.log.commit.parents.SimpleCommitParents;
import org.hanuna.gitalk.log.commit.parents.TimestampCommitParents;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CommitParser {

    private static int nextSeparatorIndex(@NotNull String line, int startIndex) {
        int nextIndex = line.indexOf("|-", startIndex);
        if (nextIndex == -1) {
            throw new IllegalArgumentException("not found separator \"|-\", with startIndex=" + startIndex +
            ", in line: " + line);
        }
        return nextIndex;
    }

    /**
     * @param line input format:
     *             ab123|-adada 193 352
     *             123|-             // no parent
     */
    @NotNull
    public static CommitParents parseCommitParents(@NotNull String line) {
        int separatorIndex = nextSeparatorIndex(line, 0);
        String commitHashStr = line.substring(0, separatorIndex);
        Hash commitHash = Hash.build(commitHashStr);

        String parentHashStr = line.substring(separatorIndex + 2, line.length());
        String[] parentsHashes = parentHashStr.split("\\s");
        List<Hash> hashes = new ArrayList<Hash>(parentsHashes.length);
        for (String aParentsStr : parentsHashes) {
            if (aParentsStr.length() > 0) {
                hashes.add(Hash.build(aParentsStr));
            }
        }
        return new SimpleCommitParents(commitHash, hashes);
    }

    /**
     *
     * @param line 1231423|-adada|-193 adf45
     * timestamp|-hash commit|-parent hashes
     */
    @NotNull
    public static TimestampCommitParents parseTimestampParentHashes(@NotNull String line) {
        int firstSeparatorIndex = nextSeparatorIndex(line, 0);
        String timestampStr = line.substring(0, firstSeparatorIndex);
        long timestamp;
        try {
            if (timestampStr.isEmpty()) {
                timestamp = 0;
            } else {
                timestamp = Long.parseLong(timestampStr);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad timestamp in line: " + line);
        }
        CommitParents commitParents = parseCommitParents(line.substring(firstSeparatorIndex + 2));

        return new TimestampCommitParents(commitParents, timestamp);
    }

    /**
     * @param line input format
     *             hash|-author name|-123124|-commit message
     */
    @NotNull
    public static CommitData parseCommitData(@NotNull String line) {
        int prevIndex = 0;
        int nextIndex = nextSeparatorIndex(line, 0);
        String hashStr = line.substring(0, nextIndex);

        prevIndex = nextIndex;
        nextIndex = nextSeparatorIndex(line, prevIndex + 1);
        String authorName = line.substring(prevIndex + 2, nextIndex);

        prevIndex = nextIndex;
        nextIndex = nextSeparatorIndex(line, prevIndex + 1);

        String timestampStr = line.substring(prevIndex + 2, nextIndex);
        long timestamp;
        try {
            if (timestampStr.isEmpty()) {
                timestamp = 0;
            } else {
                timestamp = Long.parseLong(timestampStr);
        }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad timestamp format: " + timestampStr + " in this Str: " + line);
        }

        String commitMessage = line.substring(nextIndex + 2);

        return new CommitData(Hash.build(hashStr), commitMessage, authorName, timestamp);
    }


}
