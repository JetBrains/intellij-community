package org.hanuna.gitalk.ui.tables;

import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.refs.Ref;

import java.util.List;


/**
 * @author erokhins
 */
public class GraphCommitCell extends CommitCell {

    private final GraphPrintCell row;

    public GraphCommitCell(GraphPrintCell row, String text, List<Ref> refsToThisCommit) {
        super(text, refsToThisCommit);
        this.row = row;
    }

    public GraphPrintCell getPrintCell() {
        return row;
    }

}
