/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.Ref;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

public class PlatformProjectStarter {
  public PlatformProjectStarter(MessageBus bus) {
    bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appFrameCreated(final String[] commandLineArgs, @NotNull final Ref<Boolean> willOpenProject) {
        if (commandLineArgs.length > 0) {
          willOpenProject.set(Boolean.TRUE);
        }
      }
    });
  }
}