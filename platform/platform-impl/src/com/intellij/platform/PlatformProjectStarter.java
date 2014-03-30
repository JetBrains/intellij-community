/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.platform;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Ref;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PlatformProjectStarter implements ApplicationComponent {
  public PlatformProjectStarter(MessageBus bus) {
    bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      public void appFrameCreated(final String[] commandLineArgs, @NotNull final Ref<Boolean> willOpenProject) {
        for (String arg : commandLineArgs) {
          if (!arg.equals(StartupUtil.NO_SPLASH)) {
            willOpenProject.set(true);
            break;
          }
        }
      }
    });
  }

  public void disposeComponent() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "platform.ProjectStarter";
  }

  public void initComponent() {
  }
}