package org.jetbrains.javafx;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxSupportConfigurable extends FrameworkSupportConfigurable {
  private JavaFxConfigureSdkPanel myConfigureSdkPanel;
  private JPanel myContentPane;

  @Override
  public JComponent getComponent() {
    return myContentPane;
  }

  @Override
  public void addSupport(@NotNull Module module, @NotNull ModifiableRootModel model, @Nullable Library library) {
    final Sdk sdk = myConfigureSdkPanel.getSelectedSdk();
    JavaFxUtil.addFacet(module, sdk);
  }
}
