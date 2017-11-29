package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

/**
 * Same as the base test, but detects the presence of JavaFX using imports and FXMLs, not artifacts
 * @author Pavel.Dolgov
 */
public class JavaFxFieldToPropertyNoArtifactTest extends JavaFxFieldToPropertyTest {

  public void testArtifactPresenceFieldToProperty() {
    configureByFiles(null, getTestName(false) + ".java");
    final IntentionAction intentionAction = getIntentionAction();
    // no artifact, no fxml, no javafx.* imports: the intention shouldn't be available
    assertNull(intentionAction);
  }

  public void testAddRemoveFxmlFile() {
    final VirtualFile sourceRootDir = configureByFiles(null, getTestName(false) + ".java");
    IntentionAction intentionAction = getIntentionAction();
    assertNull(intentionAction);

    final Ref<VirtualFile> fileRef = new Ref<>();
    ApplicationManager.getApplication().runWriteAction(() -> {
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
    assertNotNull("when created", intentionAction);

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        fileRef.get().delete(this);
      }
      catch (IOException e) {
        fail(e.toString());
      }
    });

    intentionAction = getIntentionAction();
    assertNull("when deleted", intentionAction);
  }

  @Override
  protected boolean isArtifactNeeded() {
    return false;
  }
}
