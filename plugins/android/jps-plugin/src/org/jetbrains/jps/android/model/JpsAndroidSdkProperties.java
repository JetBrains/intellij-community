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

import org.jetbrains.jps.model.library.JpsSdkProperties;

/**
 * @author nik
 */
public class JpsAndroidSdkProperties extends JpsSdkProperties {
  private final String myBuildTargetHashString;
  private final String myJdkName;

  public JpsAndroidSdkProperties(String homePath, String versionString, String buildTarget, String jdkName) {
    super(homePath, versionString);
    myBuildTargetHashString = buildTarget;
    myJdkName = jdkName;
  }

  public JpsAndroidSdkProperties(JpsAndroidSdkProperties properties) {
    super(properties);
    myBuildTargetHashString = properties.myBuildTargetHashString;
    myJdkName = properties.myJdkName;
  }

  public String getJdkName() {
    return myJdkName;
  }

  public String getBuildTargetHashString() {
    return myBuildTargetHashString;
  }
}
