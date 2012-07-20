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
package org.jetbrains.jps.devkit.model;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsSdkProperties;

/**
 * @author nik
 */
public class JpsIdeaSdkProperties extends JpsSdkProperties {
  private final String mySandboxHome;
  private final String myJdkName;

  public JpsIdeaSdkProperties(String homePath, String versionString, String sandboxHome, String jdkName) {
    super(homePath, versionString);
    mySandboxHome = sandboxHome;
    myJdkName = jdkName;
  }

  public JpsIdeaSdkProperties(JpsIdeaSdkProperties properties) {
    super(properties);
    mySandboxHome = properties.mySandboxHome;
    myJdkName = properties.myJdkName;
  }

  @Nullable
  public String getSandboxHome() {
    return mySandboxHome;
  }

  @Nullable
  public String getJdkName() {
    return myJdkName;
  }
}
