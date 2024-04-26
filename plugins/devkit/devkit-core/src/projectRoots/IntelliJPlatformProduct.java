// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public enum IntelliJPlatformProduct {
  IDEA("IU", "IntelliJ IDEA", null, "com.jetbrains.intellij.idea:ideaIU"),
  IDEA_IC("IC", "IntelliJ IDEA Community Edition", PlatformUtils.IDEA_CE_PREFIX, "com.jetbrains.intellij.idea:ideaIC"),
  IDEA_IE("IE", "IntelliJ IDEA Educational Edition", PlatformUtils.IDEA_EDU_PREFIX, null),
  RUBYMINE("RM", "RubyMine", PlatformUtils.RUBY_PREFIX, null),
  PYCHARM("PY", "PyCharm", PlatformUtils.PYCHARM_PREFIX, "com.jetbrains.intellij.pycharm:pycharmPY"),
  PYCHARM_PC("PC", "PyCharm Community Edition", PlatformUtils.PYCHARM_CE_PREFIX, "com.jetbrains.intellij.pycharm:pycharmPC"),
  DATASPELL("DS", "DataSpell", PlatformUtils.DATASPELL_PREFIX, null),
  PYCHARM_EDU("PE", "PyCharm Educational Edition", PlatformUtils.PYCHARM_EDU_PREFIX, null),
  PHPSTORM("PS", "PhpStorm", PlatformUtils.PHP_PREFIX, "com.jetbrains.intellij.phpstorm:phpstorm"),
  WEBSTORM("WS", "WebStorm", PlatformUtils.WEB_PREFIX, null),
  APPCODE("OC", "AppCode", PlatformUtils.APPCODE_PREFIX, null),
  CLION("CL", "CLion", PlatformUtils.CLION_PREFIX, "com.jetbrains.intellij.clion:clion"),
  MOBILE_IDE("MI", "Mobile IDE", PlatformUtils.MOBILE_IDE_PREFIX, null),
  DBE("DB", "DataGrip", PlatformUtils.DBE_PREFIX, null),
  RIDER("RD", "Rider", PlatformUtils.RIDER_PREFIX, "com.jetbrains.intellij.rider:riderRD"),
  GOIDE("GO", "GoLand", PlatformUtils.GOIDE_PREFIX, "com.jetbrains.intellij.goland:goland"),
  ANDROID_STUDIO("AI", "Android Studio", "AndroidStudio", null),
  /**
   * @deprecated Code With Me Guest is an old name for JetBrains Client
   */
  @Deprecated
  CWM_GUEST("CWMG", "Code With Me Guest", PlatformUtils.CWM_GUEST_PREFIX, null),
  JETBRAINS_CLIENT("JBC", "JetBrains Client", PlatformUtils.JETBRAINS_CLIENT_PREFIX, null),
  GATEWAY("GW", "Gateway", PlatformUtils.GATEWAY_PREFIX, "com.jetbrains.intellij.gateway:gateway"),
  FLEET_BACKEND("FLIJ", "Fleet Backend", PlatformUtils.FLEET_PREFIX, "com.jetbrains.intellij.fleetBackend:fleetBackend");

  private final String myProductCode;
  private final String myName;
  private final String myPlatformPrefix;
  private final String myMavenCoordinates;

  public @NonNls String getName() {
    return myName;
  }

  public @NonNls String getPlatformPrefix() {
    return myPlatformPrefix;
  }

  /**
   * Provides Maven coordinates which can be used for resolving artifact from the IntelliJ Repository.
   */
  public @Nullable String getMavenCoordinates() {
    return myMavenCoordinates;
  }

  IntelliJPlatformProduct(@NonNls String productCode, @NonNls String name, @NonNls String platformPrefix, @Nullable String mavenCoordinates) {
    myProductCode = productCode;
    myName = name;
    myPlatformPrefix = platformPrefix;
    myMavenCoordinates = mavenCoordinates;
  }

  public static IntelliJPlatformProduct fromBuildNumber(String buildNumber) {
    for (IntelliJPlatformProduct product : values()) {
      if (buildNumber.startsWith(product.myProductCode)) {
        return product;
      }
    }
    return IDEA;
  }

  public static @Nullable IntelliJPlatformProduct fromMavenCoordinates(String groupId, String artifactId) {
    return ContainerUtil.find(values(), product -> Objects.equals(product.getMavenCoordinates(), groupId + ":" + artifactId));
  }
}
