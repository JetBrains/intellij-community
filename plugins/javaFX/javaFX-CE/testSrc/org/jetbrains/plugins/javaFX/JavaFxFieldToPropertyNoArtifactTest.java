package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.AbstractJavaFXTestCase;

/**
 * Same as the base test, but detects the presence of JavaFX using imports and FXMLs, not artifacts
 * @author Pavel.Dolgov
 */
public class JavaFxFieldToPropertyNoArtifactTest extends JavaFxFieldToPropertyTest {
  public static final DefaultLightProjectDescriptor JAVA_FX_DESCRIPTOR_NO_ARTIFACT = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      AbstractJavaFXTestCase.addJavaFxJarAsLibrary(module, model);
      super.configureModule(module, model, contentEntry);
    }
  };

  @Override
  protected void setUpModule() {
    super.setUpModule();
  }

  public void testLongFieldToProperty() throws Exception {
    final IntentionAction intentionAction = getIntentionAction(false);
    // no artifact, no fxml, no javafx.* imports: the intention shoudn't be available
    assertNull(intentionAction);
  }
}
