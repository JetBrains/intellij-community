/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.ddms.AdbManager;
import org.jetbrains.android.ddms.AdbNotRespondingException;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.annotations.NotNull;

/**
 * @author coyote
 */
public class AndroidPlugin implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.AndroidPlugin");

  @NotNull
  public String getComponentName() {
    return "AndroidApplicationComponent";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    try {
      AdbManager.run(new Runnable() {
        public void run() {
          AndroidSdk.terminateDdmlib();
        }
      }, false);
    }
    catch (AdbNotRespondingException e) {
      LOG.info(e);
    }
  }
}
