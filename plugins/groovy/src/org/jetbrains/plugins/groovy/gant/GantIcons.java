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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author ilyas
 */
public interface GantIcons {

  Icon GANT_ICON_16x16 = IconLoader.getIcon("/org/jetbrains/plugins/groovy/images/gant_16x16.png");
  Icon GANT_SDK_ICON = IconLoader.getIcon("/org/jetbrains/plugins/groovy/images/gant_sdk.png");
  Icon GANT_NO_SDK_ICON = IconLoader.getIcon("/org/jetbrains/plugins/groovy/images/no_gant_sdk.png");
  Icon GANT_TARGET = IconLoader.getIcon("/org/jetbrains/plugins/groovy/images/gant_target.png");
  Icon GANT_TASK = IconLoader.getIcon("/org/jetbrains/plugins/groovy/images/gant_task.png");
  Icon ANT_TASK = IconLoader.getIcon("/org/jetbrains/plugins/groovy/images/ant_task.png");

}
