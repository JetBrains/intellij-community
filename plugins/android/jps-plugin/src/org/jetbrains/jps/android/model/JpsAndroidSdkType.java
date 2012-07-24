/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.android.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.library.JpsSdkProperties;
import org.jetbrains.jps.model.library.JpsSdkType;

/**
 * @author nik
 */
public class JpsAndroidSdkType extends JpsSdkType<JpsAndroidSdkProperties> implements JpsJavaSdkTypeWrapper {
  public static final JpsAndroidSdkType INSTANCE = new JpsAndroidSdkType();

  @Override
  public JpsAndroidSdkProperties createCopy(JpsAndroidSdkProperties properties) {
    return new JpsAndroidSdkProperties(properties);
  }

  @Override
  public String getJavaSdkName(@NotNull JpsSdkProperties properties) {
    return properties instanceof JpsAndroidSdkProperties ? ((JpsAndroidSdkProperties)properties).getJdkName() : null;
  }
}
