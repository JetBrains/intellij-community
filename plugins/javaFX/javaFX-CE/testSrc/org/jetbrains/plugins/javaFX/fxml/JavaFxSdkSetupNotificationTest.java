package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.java.codeInsight.daemon.impl.SdkSetupNotificationTestBase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxSdkSetupNotificationTest extends SdkSetupNotificationTestBase {
  private static final String SAMPLE_FXML = "<?import javafx.scene.layout.VBox?>\n<VBox/>";

  public void testJavaFxAsLibrary() {
    ModuleRootModificationUtil.updateModel(getModule(), model -> AbstractJavaFXTestCase.addJavaFxJarAsLibrary(model));
    final EditorNotificationPanel panel = configureBySdkAndText(IdeaTestUtil.getMockJdk18(), false, "sample.fxml", SAMPLE_FXML);
    assertNull(panel);
  }

  public void testJavaFxInProjectSdk() {
    final EditorNotificationPanel panel = configureBySdkAndText(getTestJdk(), false, "sample.fxml", SAMPLE_FXML);
    assertNull(panel);
  }

  public void testJavaFxInModuleSdk() {
    final EditorNotificationPanel panel = configureBySdkAndText(getTestJdk(), true, "sample.fxml", SAMPLE_FXML);
    assertNull(panel);
  }

  public void testNoJavaFx() {
    final EditorNotificationPanel panel = configureBySdkAndText(IdeaTestUtil.getMockJdk17(), false, "sample.fxml", SAMPLE_FXML);
    assertSdkSetupPanelShown(panel, "Setup SDK");
  }

  @NotNull
  private static Sdk getTestJdk() {
    return IdeaTestUtil.createMockJdk("testJdk", System.getProperty("java.home"), true);
  }
}
