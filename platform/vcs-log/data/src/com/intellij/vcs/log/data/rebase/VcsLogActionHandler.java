package com.intellij.vcs.log.data.rebase;

import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.RebaseCommand;

import java.util.List;

public interface VcsLogActionHandler {
  VcsLogActionHandler DO_NOTHING = new VcsLogActionHandler() {
    @Override
    public void abortRebase() {
    }

    @Override
    public void continueRebase() {
    }

    @Override
    public void cherryPick(VcsRef targetRef, List<Node> nodesToPick, Callback callback) {
    }

    @Override
    public void rebase(Node onto, VcsRef subjectRef, Callback callback) {
    }

    @Override
    public void rebaseOnto(Node onto, VcsRef subjectRef, List<Node> nodesToRebase, Callback callback) {
    }

    @Override
    public void interactiveRebase(VcsRef subjectRef, Node onto, Callback callback, List<RebaseCommand> commands) {
    }
  };

  void abortRebase();

  void continueRebase();

  interface Callback {
    void disableModifications();
    void enableModifications();

    void interactiveCommandApplied(RebaseCommand command);
  }

  void cherryPick(VcsRef targetRef, List<Node> nodesToPick, Callback callback);

  void rebase(Node onto, VcsRef subjectRef, Callback callback);
  void rebaseOnto(Node onto, VcsRef subjectRef, List<Node> nodesToRebase, Callback callback);

  void interactiveRebase(VcsRef subjectRef, Node onto, Callback callback, List<RebaseCommand> commands);
}
