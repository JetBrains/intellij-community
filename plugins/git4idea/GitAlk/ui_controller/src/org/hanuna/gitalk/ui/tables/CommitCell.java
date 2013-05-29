package org.hanuna.gitalk.ui.tables;

import org.hanuna.gitalk.refs.Ref;

import java.util.List;

/**
 * @author erokhins
 */
public class CommitCell {

    private final String text;
    private final List<Ref> refsToThisCommit;

    public CommitCell(String text, List<Ref> refsToThisCommit) {
        this.text = text;
        this.refsToThisCommit = refsToThisCommit;
    }

    public String getText() {
        return text;
    }

    public List<Ref> getRefsToThisCommit() {
        return refsToThisCommit;
    }

}
