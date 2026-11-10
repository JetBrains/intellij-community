package com.intellij.driver.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test-only utilities that must execute inside the IDE process under Driver.
 */
@TestOnly
public final class DriverTestApi {
  private DriverTestApi() {
  }

  /**
   * Blocks the IDE event dispatch thread and returns after the blocking runnable has started.
   *
   * @param millis how long the event dispatch thread should stay blocked
   */
  public static void blockEdt(long millis) {
    CountDownLatch started = new CountDownLatch(1);
    SwingUtilities.invokeLater(() -> {
      started.countDown();
      try {
        Thread.sleep(millis);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    try {
      if (!started.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for EDT blocker to start");
      }
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for EDT blocker to start", e);
    }
  }

  /**
   * Replaces the text of the document of {@code editor}, as a user input does.
   * <p>
   * Call it from any thread. It runs on the EDT in any modality, so an open modal dialog does not delay it.
   * The write runs as a user activity, so it is write-safe also in a modal dialog.
   * The modality of the editor must be the current modality.
   * Thus, the call cannot change an editor under an open modal dialog.
   *
   * @throws IllegalStateException if the modality of the editor is not the current modality
   */
  public static void setText(@NotNull Editor editor, @NotNull String text) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ModalityState currentModality = ModalityState.current();
      ModalityState editorModality = ModalityState.stateForComponent(editor.getComponent());
      if (!currentModality.equals(editorModality)) {
        throw new IllegalStateException("The editor is not in the current modality. " +
                                        "Editor modality: " + editorModality + ", current modality: " + currentModality);
      }
      ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(
        () -> WriteAction.run(() -> editor.getDocument().setText(text))
      );
    }, ModalityState.any());
  }
}
