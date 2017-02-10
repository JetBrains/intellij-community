/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public abstract class VcsCheckoutProcessor {

  public static final ExtensionPointName<VcsCheckoutProcessor> EXTENSION_POINT_NAME =
    new ExtensionPointName<>("com.intellij.vcs.checkoutProcessor");

  public static VcsCheckoutProcessor getProcessor(final @NotNull String protocol) {
    return ContainerUtil.find(EXTENSION_POINT_NAME.getExtensions(), new Condition<VcsCheckoutProcessor>() {
      @Override
      public boolean value(VcsCheckoutProcessor processor) {
        return protocol.equals(processor.getId());
      }
    });
  }

  @NotNull
  public abstract String getId();

  public abstract boolean checkout(@NotNull Map<String, String> parameters, @NotNull VirtualFile parentDirectory, @NotNull String directoryName);
}
