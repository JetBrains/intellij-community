package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.intention.IntentionAction;

/**
 * Same as the base test, but detects the presence of JavaFX using imports and FXMLs, not artifacts
 * @author Pavel.Dolgov
 */
public class JavaFxFieldToPropertyNoArtifactTest extends JavaFxFieldToPropertyTest {

  public void testArtifactPresenceFieldToProperty() throws Exception {
    final IntentionAction intentionAction = getIntentionAction(getTestName(false) + ".java");
    // no artifact, no fxml, no javafx.* imports: the intention shoudn't be available
    assertNull(intentionAction);
  }

  @Override
  protected boolean isArtifactNeeded() {
    return false;
  }
}
