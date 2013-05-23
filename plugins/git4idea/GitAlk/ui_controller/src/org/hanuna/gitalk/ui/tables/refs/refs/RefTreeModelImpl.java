package org.hanuna.gitalk.ui.tables.refs.refs;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author erokhins
 */
public class RefTreeModelImpl implements RefTreeModel {
    private final RefsModel refsModel;
    private final CommitSelectManager selectManager;
    private final RefTreeTableNode rootNode = new RefTreeTableNode("root");

    private final Map<String, RefTreeTableNode> nodeMap = new HashMap<String, RefTreeTableNode>();


    public RefTreeModelImpl(RefsModel refsModel) {
        this.refsModel = refsModel;
        this.selectManager = new CommitSelectManager(getHeadHash(refsModel));
        selectAll();
        createTree();
    }

    private Hash getHeadHash(@NotNull RefsModel refsModel) {
        List<Ref> allRefs = refsModel.getAllRefs();
        Hash headHash = allRefs.get(0).getCommitHash();
        for (Ref ref : allRefs) {
            if (ref.getName().equals("HEAD")) {
                headHash = ref.getCommitHash();
                break;
            }
        }
        return headHash;
    }

    private void selectAll() {
        for (Ref ref : refsModel.getAllRefs()) {
            if (ref.getType() != Ref.RefType.TAG) {
                selectManager.setSelectCommit(ref.getCommitHash(), true);
            }
        }
    }

    @Override
    public Set<Hash> getCheckedCommits() {
        return selectManager.getSelectCommits();
    }

    @Override
    public void inverseSelectCommit(Set<Hash> commits) {
        selectManager.inverseSelectCommit(commits);
    }

    private void createTree() {
        nodeMap.put("", rootNode);

        addCategory(Ref.RefType.LOCAL_BRANCH, "local");
        addCategory(Ref.RefType.REMOTE_BRANCH, "remotes");
        addCategory(Ref.RefType.STASH, "stash");
        addCategory(Ref.RefType.ANOTHER, "another");
    }

    private void addCategory(@NotNull Ref.RefType refType, @NotNull String categoryName) {
        for (Ref ref : refsModel.getAllRefs()) {
            if (ref.getType() == refType) {
                addNewNode(categoryName + '/' + ref.getName(), ref);
            }
        }
    }

    private void addNewNode(@NotNull String fullPatch, @NotNull Ref ref) {
        String[] folders = fullPatch.split("/");
        StringBuilder currentPatch  = new StringBuilder(fullPatch.length());
        RefTreeTableNode currentNode = rootNode;

        for (int i = 0; i < folders.length - 1; i++) {
            if (i == 0) {
                currentPatch.append(folders[0]);
            } else {
                currentPatch.append('/').append(folders[i]);
            }
            RefTreeTableNode node = nodeMap.get(currentPatch.toString());
            if (node != null) {
                currentNode = node;
            } else {
                RefTreeTableNode newNode = new RefTreeTableNode(folders[i]);
                currentNode.add(newNode);
                nodeMap.put(currentPatch.toString(), newNode);
                currentNode = newNode;
            }
            assert !currentNode.isRefNode() : "it is not leaf Ref Node!";
        }

        currentPatch.append('/').append(folders[folders.length - 1]);
        RefTreeTableNode refNode = new RefTreeTableNode(ref, selectManager);
        currentNode.add(refNode);
    }

    @Override
    public RefTreeTableNode getRootNode() {
        return rootNode;
    }
}
