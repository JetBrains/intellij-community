// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NonNls;


public enum IntelliJPlatformProduct {
  IDEA("IU", "IntelliJ IDEA", null),
  IDEA_IC("IC", "IntelliJ IDEA Community Edition", PlatformUtils.IDEA_CE_PREFIX),
  IDEA_IE("IE", "IntelliJ IDEA Educational Edition", PlatformUtils.IDEA_EDU_PREFIX),
  RUBYMINE("RM", "RubyMine", PlatformUtils.RUBY_PREFIX),
  PYCHARM("PY", "PyCharm", PlatformUtils.PYCHARM_PREFIX),
  PYCHARM_PC("PC", "PyCharm Community Edition", PlatformUtils.PYCHARM_CE_PREFIX),
  DATASPELL("DS", "DataSpell", PlatformUtils.DATASPELL_PREFIX),
  PYCHARM_EDU("PE", "PyCharm Educational Edition", PlatformUtils.PYCHARM_EDU_PREFIX),
  PHPSTORM("PS", "PhpStorm", PlatformUtils.PHP_PREFIX),
  WEBSTORM("WS", "WebStorm", PlatformUtils.WEB_PREFIX),
  APPCODE("OC", "AppCode", PlatformUtils.APPCODE_PREFIX),
  CLION("CL", "CLion", PlatformUtils.CLION_PREFIX),
  MOBILE_IDE("MI", "Mobile IDE", PlatformUtils.MOBILE_IDE_PREFIX),
  DBE("DB", "DataGrip", PlatformUtils.DBE_PREFIX),
  RIDER("RD", "Rider", PlatformUtils.RIDER_PREFIX),
  GOIDE("GO", "GoLand", PlatformUtils.GOIDE_PREFIX),
  ANDROID_STUDIO("AI", "Android Studio", "AndroidStudio"),
  /**
   * @deprecated Code With Me Guest is an old name for JetBrains Client
   */
  @Deprecated
  CWM_GUEST("CWMG", "Code With Me Guest", PlatformUtils.CWM_GUEST_PREFIX),
  JETBRAINS_CLIENT("JBC", "JetBrains Client", PlatformUtils.JETBRAINS_CLIENT_PREFIX),
  GATEWAY("GW", "Gateway", PlatformUtils.GATEWAY_PREFIX);

  private final String myProductCode;
  private final String myName;
  private final String myPlatformPrefix;

  public @NonNls String getName() {
    return myName;
  }

  public @NonNls String getPlatformPrefix() {
    return myPlatformPrefix;
  }

  IntelliJPlatformProduct(@NonNls String productCode,@NonNls String name, @NonNls String platformPrefix) {
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
