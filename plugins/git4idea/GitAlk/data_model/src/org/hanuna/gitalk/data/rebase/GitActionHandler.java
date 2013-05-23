package org.hanuna.gitalk.data.rebase;

import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.refs.Ref;

import java.util.List;

public interface GitActionHandler {
  interface Callback {
    void disableModifications();
    void enableModifications();

    void interactiveCommandApplied(RebaseCommand command);
  }

  void cherryPick(Ref targetRef, List<Node> nodesToPick, Callback callback);

  void rebase(Node onto, Ref subjectRef, Callback callback);
  void rebaseOnto(Node onto, Ref subjectRef, List<Node> nodesToRebase, Callback callback);

  void interactiveRebase(Ref subjectRef, List<RebaseCommand> commands, Callback callback);

  enum InsertPosition {
    ABOVE,
    BELOW
  }
}
