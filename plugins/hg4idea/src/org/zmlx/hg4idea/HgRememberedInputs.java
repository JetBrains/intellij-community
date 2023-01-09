// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;

/**
 * @author Nadya Zabrodina
 */
@State(
  name = "HgRememberedInputs",
  storages = {
    @Storage(value = "vcs.xml", deprecated = true),
    @Storage(value = "vcs-inputs.xml", roamingType = RoamingType.DISABLED)
  },
  category = SettingsCategory.TOOLS
)
public class HgRememberedInputs extends DvcsRememberedInputs implements PersistentStateComponent<DvcsRememberedInputs.State> {
  public static DvcsRememberedInputs getInstance() {
    return ApplicationManager.getApplication().getService(HgRememberedInputs.class);
  }
}
