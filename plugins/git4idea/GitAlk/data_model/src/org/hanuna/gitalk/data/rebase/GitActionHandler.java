package org.hanuna.gitalk.data.rebase;

import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.parents.RebaseCommand;
import org.hanuna.gitalk.refs.Ref;

import java.util.List;

public interface GitActionHandler {
  GitActionHandler DO_NOTHING = new GitActionHandler() {
    @Override
    public void cherryPick(Ref targetRef, List<Node> nodesToPick, Callback callback) {
    }

    @Override
    public void rebase(Node onto, Ref subjectRef, Callback callback) {
    }

    @Override
    public void rebaseOnto(Node onto, Ref subjectRef, List<Node> nodesToRebase, Callback callback) {
    }

    @Override
    public void interactiveRebase(Ref subjectRef, Node onto, Callback callback, List<RebaseCommand> commands) {
    }
  };

  interface Callback {
    void disableModifications();
    void enableModifications();

    void interactiveCommandApplied(RebaseCommand command);
  }

  void cherryPick(Ref targetRef, List<Node> nodesToPick, Callback callback);

  void rebase(Node onto, Ref subjectRef, Callback callback);
  void rebaseOnto(Node onto, Ref subjectRef, List<Node> nodesToRebase, Callback callback);

  void interactiveRebase(Ref subjectRef, Node onto, Callback callback, List<RebaseCommand> commands);
}
