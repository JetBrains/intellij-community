package org.jetbrains.debugger;

import com.intellij.util.Function;
import gnu.trove.THashMap;
import org.chromium.v8.liveEditProtocol.LiveEditResult;
import org.jetbrains.io.JsonReaderEx;

import java.util.AbstractList;
import java.util.List;
import java.util.Map;

public class UpdateResultParser {
  private static final Map<String, ScriptLiveChangeResult.ChangeStatus> statusCodes;

  static {
    statusCodes = new THashMap<String, ScriptLiveChangeResult.ChangeStatus>(5);
    statusCodes.put("unchanged", ScriptLiveChangeResult.ChangeStatus.UNCHANGED);
    statusCodes.put("source changed", ScriptLiveChangeResult.ChangeStatus.NESTED_CHANGED);
    statusCodes.put("changed", ScriptLiveChangeResult.ChangeStatus.CODE_PATCHED);
    statusCodes.put("damaged", ScriptLiveChangeResult.ChangeStatus.DAMAGED);
  }

  static final Function<LiveEditResult.OldTreeNode, ScriptLiveChangeResult.OldFunctionNode> OLD_WRAPPER =
    new Function<LiveEditResult.OldTreeNode, ScriptLiveChangeResult.OldFunctionNode>() {
      @Override
      public ScriptLiveChangeResult.OldFunctionNode fun(LiveEditResult.OldTreeNode original) {
        return new OldFunctionNodeImpl(original);
      }
    };

  private static final Function<LiveEditResult.NewTreeNode, ScriptLiveChangeResult.NewFunctionNode> NEW_WRAPPER =
    new Function<LiveEditResult.NewTreeNode, ScriptLiveChangeResult.NewFunctionNode>() {
      @Override
      public ScriptLiveChangeResult.NewFunctionNode fun(LiveEditResult.NewTreeNode original) {
        return new NewFunctionNodeImpl(original);
      }
    };

  public static ScriptLiveChangeResult wrapChangeDescription(final LiveEditResult previewDescription, final JsonReaderEx changeLog) {
    return previewDescription == null ? null : new ScriptLiveChangeResult(changeLog, previewDescription);
  }

  private static class OldFunctionNodeImpl implements ScriptLiveChangeResult.OldFunctionNode {
    private final LiveEditResult.OldTreeNode treeNode;
    private final ScriptLiveChangeResult.FunctionPositions positions;
    private final ScriptLiveChangeResult.FunctionPositions newPositions;

    OldFunctionNodeImpl(LiveEditResult.OldTreeNode treeNode) {
      this.treeNode = treeNode;
      positions = wrapPositions(treeNode.positions());
      if (treeNode.new_positions() == null) {
        newPositions = null;
      }
      else {
        newPositions = wrapPositions(treeNode.new_positions());
      }
    }

    @Override
    public String getName() {
      return treeNode.name();
    }

    @Override
    public ScriptLiveChangeResult.ChangeStatus getStatus() {
      return statusCodes.get(treeNode.status());
    }

    @Override
    public String getStatusExplanation() {
      return treeNode.status_explanation();
    }

    @Override
    public List<? extends ScriptLiveChangeResult.OldFunctionNode> children() {
      return wrapList(treeNode.children(), OLD_WRAPPER);
    }

    @Override
    public List<? extends ScriptLiveChangeResult.NewFunctionNode> newChildren() {
      return wrapList(treeNode.new_children(), NEW_WRAPPER);
    }

    @Override
    public ScriptLiveChangeResult.FunctionPositions getPositions() {
      return positions;
    }

    @Override
    public ScriptLiveChangeResult.FunctionPositions getNewPositions() {
      return newPositions;
    }

    @Override
    public ScriptLiveChangeResult.OldFunctionNode asOldFunction() {
      return this;
    }
  }

  private static class NewFunctionNodeImpl implements ScriptLiveChangeResult.NewFunctionNode {
    private final LiveEditResult.NewTreeNode treeNode;
    private final ScriptLiveChangeResult.FunctionPositions positions;

    NewFunctionNodeImpl(LiveEditResult.NewTreeNode treeNode) {
      this.treeNode = treeNode;
      positions = wrapPositions(treeNode.positions());
    }

    @Override
    public String getName() {
      return treeNode.name();
    }

    @Override
    public ScriptLiveChangeResult.FunctionPositions getPositions() {
      return positions;
    }

    @Override
    public List<? extends ScriptLiveChangeResult.NewFunctionNode> children() {
      return wrapList(treeNode.children(), NEW_WRAPPER);
    }

    @Override
    public ScriptLiveChangeResult.OldFunctionNode asOldFunction() {
      return null;
    }
  }

  private static ScriptLiveChangeResult.FunctionPositions wrapPositions(final LiveEditResult.Positions rawPositions) {
    return new ScriptLiveChangeResult.FunctionPositions() {
      @Override
      public long getStart() {
        return rawPositions.start_position();
      }

      @Override
      public long getEnd() {
        return rawPositions.end_position();
      }
    };
  }

  private static <FROM, TO> List<TO> wrapList(final List<? extends FROM> originalList, final Function<FROM, TO> wrapper) {
    return new AbstractList<TO>() {
      @Override
      public TO get(int index) {
        return wrapper.fun(originalList.get(index));
      }

      @Override
      public int size() {
        return originalList.size();
      }
    };
  }
}