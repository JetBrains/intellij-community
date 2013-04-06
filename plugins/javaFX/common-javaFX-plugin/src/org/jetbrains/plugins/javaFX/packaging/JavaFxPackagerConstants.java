/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.packaging;

import org.jetbrains.annotations.NonNls;

/**
 * User: anna
 * Date: 3/13/13
 */
public class JavaFxPackagerConstants {
  @NonNls public static final String UPDATE_MODE_BACKGROUND = "background";
  @NonNls public static final String UPDATE_MODE_ALWAYS = "always";
  @NonNls public static final String DEFAULT_HEIGHT = "400";
  @NonNls public static final String DEFAULT_WEIGHT = "600";

  public enum NativeBundles {
    none, all, deb, dmg, exe, image, msi, rpm
  } 
}
