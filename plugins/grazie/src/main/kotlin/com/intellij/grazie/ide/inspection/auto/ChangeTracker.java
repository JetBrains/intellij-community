package com.intellij.grazie.ide.inspection.auto;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public final class ChangeTracker implements Disposable {
  private static final Key<List<RecentChange>> DATA_KEY = Key.create("Grazie.Pro.RecentChanges");
  private static final Key<List<RangeMarker>> UNDONE_RANGES = Key.create("Grazie.Pro.UndoneChanges");

  ChangeTracker() {
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        Editor[] editors = EditorFactory.getInstance().getEditors(event.getDocument());
        if (editors.length == 0) return;

        if (isEditing(editors)) {
          registerChange(event);
        } else if (isUndoInProgress(editors)) {
          registerUndo(event);
        }
      }
    }, this);
  }

  private static boolean isEditing(Editor[] editors) {
    String cmdName = CommandProcessor.getInstance().getCurrentCommandName();
    return EditorBundle.message("typing.in.editor.command.name").equals(cmdName) ||
           CodeInsightBundle.message("completion.automatic.command.name").equals(cmdName) ||
           Arrays.stream(editors).anyMatch(e -> LookupManager.getActiveLookup(e) != null);
  }

  private static boolean isUndoInProgress(Editor[] editors) {
    return Arrays.stream(editors)
      .anyMatch(e -> e.getProject() != null && UndoManager.getInstance(e.getProject()).isUndoInProgress());
  }

  private synchronized void registerChange(DocumentEvent event) {
    Document document = event.getDocument();
    List<RecentChange> changes = document.getUserData(DATA_KEY);
    if (changes == null) {
      document.putUserData(DATA_KEY, changes = new ArrayList<>());
    }

    long now = System.nanoTime();
    for (Iterator<RecentChange> iterator = changes.iterator(); iterator.hasNext(); ) {
      RecentChange change = iterator.next();
      if (!change.isRelevant(now)) {
        change.marker.dispose();
        iterator.remove();
      }
    }

    changes.add(new RecentChange(now, createMarker(event)));
  }

  @NotNull
  private static RangeMarker createMarker(DocumentEvent event) {
    return event.getDocument().createRangeMarker(TextRange.from(event.getOffset(), event.getNewLength()));
  }

  private synchronized void registerUndo(DocumentEvent event) {
    Document document = event.getDocument();
    var ranges = document.getUserData(UNDONE_RANGES);
    if (ranges == null) {
      document.putUserData(UNDONE_RANGES, ranges = new ArrayList<>());
    }
    ranges.removeIf(r -> !r.isValid());
    ranges.add(createMarker(event));
  }

  public static ChangeTracker getInstance() {
    return ApplicationManager.getApplication().getService(ChangeTracker.class);
  }

  synchronized boolean mayAutoChange(Document document, TextRange range) {
    var changes = document.getUserData(DATA_KEY);
    if (changes == null) return false;

    long now = System.nanoTime();
    return changes.stream().anyMatch(c -> c.isRelevant(now) && c.marker.getTextRange().intersects(range));
  }

  synchronized boolean isExplicitlyUndone(Document document, TextRange range) {
    var undone = document.getUserData(UNDONE_RANGES);
    return undone != null && undone.stream().anyMatch(r -> r.isValid() && r.getTextRange().intersects(range));
  }

  @Override
  public void dispose() {
  }

  private record RecentChange(long time, RangeMarker marker) {
    private static final long TRACKING_INTERVAL = TimeUnit.MINUTES.toNanos(1);

    boolean isRelevant(long now) {
      return marker.isValid() && now - time <= TRACKING_INTERVAL;
    }
  }
}
