/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.util.PlatformUtils;

/**
 * @author yole
 */
public enum IntelliJPlatformProduct {
  IDEA("IU", "IntelliJ IDEA", null),
  IDEA_IC("IC", "IntelliJ IDEA Community Edition", PlatformUtils.IDEA_CE_PREFIX),
  RUBYMINE("RM", "RubyMine", PlatformUtils.RUBY_PREFIX),
  PYCHARM("PY", "PyCharm", PlatformUtils.PYCHARM_PREFIX),
  PYCHARM_PC("PC", "PyCharm Community Edition", PlatformUtils.PYCHARM_CE_PREFIX),
  PYCHARM_EDU("PE", "PyCharm Educational Edition", PlatformUtils.PYCHARM_EDU_PREFIX),
  PHPSTORM("PS", "PhpStorm", PlatformUtils.PHP_PREFIX),
  WEBSTORM("WS", "WebStorm", PlatformUtils.WEB_PREFIX),
  APPCODE("OC", "AppCode", PlatformUtils.APPCODE_PREFIX),
  CLION("CL", "CLion", PlatformUtils.CLION_PREFIX),
  DBE("DB", "DataGrip", PlatformUtils.DBE_PREFIX),
  RIDER("RD", "Rider", PlatformUtils.RIDER_PREFIX),
  GOIDE("GO", "GoLand", PlatformUtils.GOIDE_PREFIX),
  ANDROID_STUDIO("AI", "Android Studio", "AndroidStudio");

  private final String myProductCode;
  private final String myName;
  private final String myPlatformPrefix;

  public String getName() {
    return myName;
  }

  public String getPlatformPrefix() {
    return myPlatformPrefix;
  }

  IntelliJPlatformProduct(String productCode, String name, String platformPrefix) {
    myProductCode = productCode;
    myName = name;
    myPlatformPrefix = platformPrefix;
  }

  public static IntelliJPlatformProduct fromBuildNumber(String buildNumber) {
    for (IntelliJPlatformProduct product : values()) {
      if (buildNumber.startsWith(product.myProductCode)) {
        return product;
      }
    }
    return IDEA;
  }
}
