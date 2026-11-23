// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.daemon.DaemonAnalyzerFixture;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Same as the base test, but detects the presence of JavaFX using imports and FXMLs, not artifacts
 */
public class JavaFxFieldToPropertyNoArtifactTest extends JavaFxFieldToPropertyTest {

  @Override
  @Test
  public void testArtifactPresenceFieldToProperty() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.configureByFiles(null, fixture.getTestName(false) + ".java");
    final IntentionAction intentionAction = getIntentionAction();
    // no artifact, no fxml, no javafx.* imports: the intention shouldn't be available
    assertNull(intentionAction);
  }

  @Test
  public void testAddRemoveFxmlFile() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    final VirtualFile sourceRootDir = fixture.configureByFiles(null, fixture.getTestName(false) + ".java");
    IntentionAction intentionAction = getIntentionAction();
    assertNull(intentionAction);

    final Ref<VirtualFile> fileRef = new Ref<>();
    WriteAction.runAndWait(() -> {
      try {
        VirtualFile file = sourceRootDir.createChildData(this, "sample.fxml");
        VfsUtil.saveText(file, "<?import javafx.scene.layout.VBox?>\n<VBox/>");
        fileRef.set(file);
      }
      catch (IOException e) {
        fail(e.toString());
      }
    });

    intentionAction = getIntentionAction();
    assertNotNull(intentionAction, "when created");

    WriteAction.runAndWait(() -> {
      try {
        fileRef.get().delete(this);
      }
      catch (IOException e) {
        fail(e.toString());
      }
    });

    intentionAction = getIntentionAction();
    assertNull(intentionAction, "when deleted");
  }

  @Override
  protected boolean isArtifactNeeded() {
    return false;
  }
}
