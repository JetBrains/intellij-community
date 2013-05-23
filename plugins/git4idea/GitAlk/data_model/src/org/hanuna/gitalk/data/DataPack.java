package org.hanuna.gitalk.data;

import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface DataPack {

    public CommitDataGetter getCommitDataGetter();

    @NotNull
    public RefsModel getRefsModel();

    @NotNull
    public GraphModel getGraphModel();

    @NotNull
    public GraphPrintCellModel getPrintCellModel();

}
