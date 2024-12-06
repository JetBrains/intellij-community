package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.graph.GraphManager;
import com.intellij.openapi.graph.base.Command;
import com.intellij.openapi.graph.base.EdgeCursor;
import com.intellij.openapi.graph.base.NodeCursor;
import com.intellij.openapi.graph.builder.GraphBuilder;
import com.intellij.openapi.graph.services.GraphUndoService;
import com.intellij.openapi.graph.view.Graph2DUndoManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class JourneyGraphUndoService implements GraphUndoService {
  @Override
  public @NotNull Graph2DUndoManager setupUndoManagerFor(@NotNull GraphBuilder<?, ?> builder) {
    final var graphUndoManager = GraphManager.getGraphManager().createGraph2DUndoManager(builder.getGraph());
    graphUndoManager.addUndoListener(new Graph2DUndoManager.UndoListener() {
      @Override
      public void commandAdded(Command command) {
      }
    });
    return graphUndoManager;
  }

  @RequiresWriteLock
  private @NotNull Pair<Runnable, Runnable> beforePositionsOnlyChangingActionPerformed(
    @NotNull GraphBuilder<?, ?> builder,
    @NotNull Consumer<? super Runnable> onUndo,
    @NotNull Consumer<? super Runnable> onRedo
  ) {
    final var graph = builder.getGraph();
    graph.firePreEvent();    // begins single graph undo section for several commands
    return backupRealizers(builder, graph.nodes(), graph.edges(), onUndo, onRedo);
  }

  @Override
  public void performPositionsOnlyChangingAction(
    @NotNull GraphBuilder<?, ?> builder,
    @Nls @NotNull String commandName,
    @NotNull BiConsumer<? super Runnable, ? super Runnable> action,
    @NotNull Consumer<? super Runnable> onUndo,
    @NotNull Consumer<? super Runnable> onRedo
  ) {
    final var recordRealizersHandles = WriteAction.compute(() -> beforePositionsOnlyChangingActionPerformed(builder, onUndo, onRedo));
    action.accept(recordRealizersHandles.getFirst(), recordRealizersHandles.getSecond());
    WriteCommandAction.writeCommandAction(builder.getProject()).withName(commandName).run(() -> {
      afterPositionsOnlyChangingActionPerformed(builder, new Command() {
        @Override
        public void execute() {
          action.accept(recordRealizersHandles.getFirst(), recordRealizersHandles.getSecond());
        }

        @Override
        public void undo() {
        }

        @Override
        public void redo() {
        }
      });
    });
  }

  @RequiresWriteLock
  private void afterPositionsOnlyChangingActionPerformed(@NotNull GraphBuilder<?, ?> builder, @NotNull Command command) {
    undoableActionPerformed(builder, command); // pushes command on the graph and IDEA stack
    builder.getGraph().firePostEvent();        // ends graph undo section
  }

  @Override
  public @NotNull Pair<Runnable, Runnable> backupRealizers(
    @NotNull GraphBuilder<?, ?> builder,
    @NotNull NodeCursor nodeCursor,
    @NotNull EdgeCursor edgeCursor,
    @NotNull Consumer<? super Runnable> onUndo,
    @NotNull Consumer<? super Runnable> onRedo
  ) {
    return Pair.create(() -> {}, () -> {});
  }
}
