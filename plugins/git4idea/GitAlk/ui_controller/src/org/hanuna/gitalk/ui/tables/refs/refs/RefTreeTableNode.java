package org.hanuna.gitalk.ui.tables.refs.refs;

import org.hanuna.gitalk.refs.Ref;
import org.jdesktop.swingx.treetable.AbstractMutableTreeTableNode;
import org.jdesktop.swingx.treetable.MutableTreeTableNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public class RefTreeTableNode extends AbstractMutableTreeTableNode {

    @Nullable
    private final Ref ref;

    @Nullable
    private final String text;

    // notNull if isRefNode()
    @Nullable
    private final CommitSelectManager selectManager;

    private RefTreeTableNode(@Nullable Ref ref, @Nullable String text, @Nullable CommitSelectManager selectManager) {
        this.ref = ref;
        this.text = text;
        this.selectManager = selectManager;
    }

    public RefTreeTableNode(@NotNull Ref ref, @NotNull CommitSelectManager selectManager) {
        this(ref, null, selectManager);
    }

    public RefTreeTableNode(@NotNull String text) {
        this(null, text, null);
    }

    public boolean isRefNode() {
        return ref != null;
    }

    @Nullable
    public Ref getRef() {
        return ref;
    }

    @Nullable
    public String getText() {
        return text;
    }

    private boolean isSelectNode() {
        if (isRefNode()) {
            return selectManager.isSelect(ref.getCommitHash());
        } else {
            boolean select = true;
            for (RefTreeTableNode children : new IterableEnumeration<MutableTreeTableNode, RefTreeTableNode>(children())) {
                if (!children.isSelectNode()) {
                    select = false;
                }
            }
            return select;
        }
    }

    private void setSelect(boolean select) {
        if (isRefNode()) {
            selectManager.setSelectCommit(ref.getCommitHash(), select);
        } else {
            for (RefTreeTableNode children : new IterableEnumeration<MutableTreeTableNode, RefTreeTableNode>(children())) {
                children.setSelect(select);
            }
        }
    }

    @Override
    public Object getValueAt(int column) {
        switch (column) {
            case 0:
                return isSelectNode();
            case 1:
                if (isRefNode()) {
                    return ref;
                } else {
                    return text;
                }
            default:
                throw new IllegalArgumentException("bad column number: " + column);
        }
    }

    @Override
    public int getColumnCount() {
        return 2;
    }


    @Override
    public void setValueAt(Object aValue, int column) {
        if (column != 0) {
            throw new IllegalArgumentException("Not allow change column: " + column);
        }
        setSelect((Boolean) aValue);
    }

    @Override
    public String toString() {
        return "RefTreeTableNode{" +
                "ref=" + ref +
                ", text='" + text + '\'' +
                '}';
    }
}
