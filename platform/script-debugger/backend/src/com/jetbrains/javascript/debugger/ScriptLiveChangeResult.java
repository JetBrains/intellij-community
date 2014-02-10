package com.jetbrains.javascript.debugger;

import org.chromium.v8.liveEditProtocol.LiveEditResult;
import org.jetbrains.io.JsonReaderEx;

import java.util.List;

public class ScriptLiveChangeResult {
  private final JsonReaderEx changeLog;
  private final LiveEditResult previewDescription;

  public ScriptLiveChangeResult(JsonReaderEx changeLog, LiveEditResult previewDescription) {
    this.changeLog = changeLog;
    this.previewDescription = previewDescription;
  }

  public JsonReaderEx getChangeLog() {
    return changeLog;
  }

  public OldFunctionNode getChangeTree() {
    return UpdateResultParser.OLD_WRAPPER.fun(previewDescription.change_tree());
  }

  public String getCreatedScriptName() {
    return previewDescription.created_script_name();
  }

  public boolean isStackModified() {
    return previewDescription.stack_modified();
  }

  public TextualDiff getTextualDiff() {
    final LiveEditResult.TextualDiff protocolTextualData = previewDescription.textual_diff();
    if (protocolTextualData == null) {
      return null;
    }
    return new TextualDiff() {
      @Override
      public int[] getChunks() {
        return protocolTextualData.chunks();
      }
    };
  }

  public enum ChangeStatus {
    UNCHANGED,
    NESTED_CHANGED,
    CODE_PATCHED,
    DAMAGED
  }

  public interface TextualDiff {
    /**
     * @return textual diff of the script in form of list of 3-element diff chunk parameters
     *   that are (old_start_pos, old_end_pos, new_end_pos)
     */
    int[] getChunks();
  }

  public interface FunctionNode<T extends FunctionNode<T>> {
    String getName();
    FunctionPositions getPositions();
    List<? extends T> children();
    OldFunctionNode asOldFunction();
  }

  public interface FunctionPositions {
    long getStart();
    long getEnd();
  }

  /**
   * Represents an old function in the changed script. If it has new positions, it is also
   * represented in a new version of the script.
   */
  public interface OldFunctionNode extends FunctionNode<OldFunctionNode> {
    ChangeStatus getStatus();

    String getStatusExplanation();

    /** @return nullable */
    FunctionPositions getNewPositions();

    List<? extends NewFunctionNode> newChildren();
  }

  /**
   * Represents a brand new function in the changed script, that has no corresponding old function.
   */
  public interface NewFunctionNode extends FunctionNode<NewFunctionNode> {
  }
}