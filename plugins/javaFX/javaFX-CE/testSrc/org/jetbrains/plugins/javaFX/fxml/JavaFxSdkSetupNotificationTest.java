package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationsImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFxProjectSdkSetupValidator;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxSdkSetupNotificationTest extends JavaCodeInsightFixtureTestCase {
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
    return ((JavaSdkImpl)JavaSdk.getInstance()).createMockJdk("testJdk", System.getProperty("java.home"), true);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    setProjectSdk(IdeaTestUtil.getMockJdk17());
  }

  @Nullable
  @SuppressWarnings("SameParameterValue")
  private EditorNotificationPanel configureBySdkAndText(@Nullable Sdk sdk,
                                                        boolean isModuleSdk,
                                                        @NotNull String name,
                                                        @NotNull String text) {
    if (isModuleSdk) {
      ModuleRootModificationUtil.setModuleSdk(getModule(), sdk);
    }
    else {
      setProjectSdk(sdk);
      ModuleRootModificationUtil.setSdkInherited(getModule());
    }

    final PsiFile psiFile = myFixture.configureByText(name, text);
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(getProject());
    VirtualFile virtualFile = psiFile.getVirtualFile();
    final FileEditor[] editors = fileEditorManager.openFile(virtualFile, true);
    Disposer.register(myFixture.getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        fileEditorManager.closeFile(virtualFile);
      }
    });
    assertThat(editors).hasSize(1);
    EditorNotificationsImpl.completeAsyncTasks();

    return editors[0].getUserData(JavaFxProjectSdkSetupValidator.KEY);
  }

  private void setProjectSdk(@Nullable Sdk sdk) {
    if (sdk != null) {
      final Sdk foundJdk = ReadAction.compute(() -> ProjectJdkTable.getInstance().findJdk(sdk.getName()));
      if (foundJdk == null) {
        WriteAction.run(() -> ProjectJdkTable.getInstance().addJdk(sdk, myFixture.getProjectDisposable()));
      }
    }
    WriteAction.run(() -> ProjectRootManager.getInstance(getProject()).setProjectSdk(sdk));
  }

  @SuppressWarnings("SameParameterValue")
  private static void assertSdkSetupPanelShown(@Nullable EditorNotificationPanel panel,
                                               @NotNull String expectedMessagePrefix) {
    assertThat(panel).isNotNull();
    final IntentionActionWithOptions action = panel.getIntentionAction();
    assertThat(action).isNotNull();
    final String text = action.getText();
    assertThat(text).isNotNull();
    if (!text.startsWith(expectedMessagePrefix)) {
      final int length = Math.min(text.length(), expectedMessagePrefix.length());
      assertThat(text.substring(0, length)).isEqualTo(expectedMessagePrefix);
    }
  }
}
