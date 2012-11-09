package org.jetbrains.android.newProject;

import com.intellij.ide.util.projectWizard.SdkSettingsStep;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
* @author Eugene.Kudelevsky
*/
class AndroidModifiedSettingsStep extends SdkSettingsStep {
  protected AndroidModuleBuilder myBuilder;

  AndroidModifiedSettingsStep(@NotNull final AndroidModuleBuilder builder, @NotNull SettingsStep settingsStep) {
    super(settingsStep, builder, new Condition<SdkTypeId>() {
      @Override
      public boolean value(SdkTypeId sdkType) {
        return builder.isSuitableSdkType(sdkType);
      }
    });
    myBuilder = builder;
  }

  @Override
  public void updateDataModel() {
    super.updateDataModel();
    final String path = myBuilder.getContentEntryPath();

    if (path != null) {
      myBuilder.setSourcePaths(Collections.singletonList(Pair.create(path + "/src", "")));
    }
  }
}
