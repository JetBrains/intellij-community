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
package org.jetbrains.android.maven;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMavenUtil {
  @NonNls public static final String APKSOURCES_DEPENDENCY_TYPE = "apksources";
  @NonNls public static final String APKLIB_DEPENDENCY_AND_PACKAGING_TYPE = "apklib";

  private AndroidMavenUtil() {
  }

  public static boolean isMavenizedModule(@NotNull Module module) {
    AndroidMavenProvider mavenProxy = getMavenProvider();
    return mavenProxy != null && mavenProxy.isMavenizedModule(module);
  }

  @Nullable
  public static AndroidMavenProvider getMavenProvider() {
    AndroidMavenProvider[] extensions = AndroidMavenProvider.EP_NAME.getExtensions();
    return extensions.length > 0 ? extensions[0] : null;
  }
}
