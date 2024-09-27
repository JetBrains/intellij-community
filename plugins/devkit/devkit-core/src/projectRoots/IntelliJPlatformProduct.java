// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.projectRoots;

import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public enum IntelliJPlatformProduct {
  IDEA("IU", "IntelliJ IDEA", null, "com.jetbrains.intellij.idea:ideaIU", "idea:ideaIU"),
  IDEA_IC("IC", "IntelliJ IDEA Community Edition", PlatformUtils.IDEA_CE_PREFIX, "com.jetbrains.intellij.idea:ideaIC", "idea:ideaIC"),
  IDEA_IE("IE", "IntelliJ IDEA Educational Edition", PlatformUtils.IDEA_EDU_PREFIX, null, null),
  RUBYMINE("RM", "RubyMine", PlatformUtils.RUBY_PREFIX, null, "ruby:RubyMine"),
  PYCHARM("PY", "PyCharm", PlatformUtils.PYCHARM_PREFIX, "com.jetbrains.intellij.pycharm:pycharmPY", "python:pycharm-professional"),
  PYCHARM_PC("PC", "PyCharm Community Edition", PlatformUtils.PYCHARM_CE_PREFIX, "com.jetbrains.intellij.pycharm:pycharmPC", "python:pycharm-community"),
  DATASPELL("DS", "DataSpell", PlatformUtils.DATASPELL_PREFIX, null, "python:dataspell"),
  PYCHARM_EDU("PE", "PyCharm Educational Edition", PlatformUtils.PYCHARM_EDU_PREFIX, null, null),
  PHPSTORM("PS", "PhpStorm", PlatformUtils.PHP_PREFIX, "com.jetbrains.intellij.phpstorm:phpstorm", "webide:PhpStorm"),
  WEBSTORM("WS", "WebStorm", PlatformUtils.WEB_PREFIX, "com.jetbrains.intellij.webstorm:webstorm", "webstorm:WebStorm"),
  APPCODE("OC", "AppCode", PlatformUtils.APPCODE_PREFIX, null, null),
  CLION("CL", "CLion", PlatformUtils.CLION_PREFIX, "com.jetbrains.intellij.clion:clion", "cpp:CLion"),
  DBE("DB", "DataGrip", PlatformUtils.DBE_PREFIX, null, "datagrip:datagrip"),
  RIDER("RD", "Rider", PlatformUtils.RIDER_PREFIX, "com.jetbrains.intellij.rider:riderRD", "rider:JetBrains.Rider"),
  GOIDE("GO", "GoLand", PlatformUtils.GOIDE_PREFIX, "com.jetbrains.intellij.goland:goland", "go:goland"),
  ANDROID_STUDIO("AI", "Android Studio", "AndroidStudio", null, "com.google.android.studio:android-studio"),
  /**
   * @deprecated Code With Me Guest is an old name for JetBrains Client
   */
  @Deprecated
  CWM_GUEST("CWMG", "Code With Me Guest", PlatformUtils.CWM_GUEST_PREFIX, null, null),
  JETBRAINS_CLIENT("JBC", "JetBrains Client", PlatformUtils.JETBRAINS_CLIENT_PREFIX, null, null),
  GATEWAY("GW", "Gateway", PlatformUtils.GATEWAY_PREFIX, "com.jetbrains.intellij.gateway:gateway", "idea/gateway:JetBrainsGateway"),
  FLEET_BACKEND("FLIJ", "Fleet Backend", PlatformUtils.FLEET_PREFIX, "com.jetbrains.intellij.fleetBackend:fleetBackend", null),
  AQUA("QA", "Aqua", PlatformUtils.AQUA_PREFIX, null, "aqua:aqua"),
  RUSTROVER("RR", "RustRover", PlatformUtils.RUSTROVER_PREFIX, "com.jetbrains.intellij.rustrover:RustRover", "rustrover:RustRover"),
  WRITERSIDE("WRS", "Writerside", PlatformUtils.WRITERSIDE_PREFIX, "com.jetbrains.intellij.idea:writerside", "writerside:writerside");

  private final String myProductCode;
  private final String myName;
  private final String myPlatformPrefix;
  private final String myMavenCoordinates;
  private final String myCdnCoordinates;

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

  /**
   * Provides CDN coordinates which can be used for resolving artifact from the download.jetbrains.com CDN.
   */
  public @Nullable String getCdnCoordinates() {
    return myCdnCoordinates;
  }

  IntelliJPlatformProduct(
    @NonNls String productCode,
    @NonNls String name,
    @NonNls String platformPrefix,
    @Nullable String mavenCoordinates,
    @Nullable String cdnCoordinates
  ) {
    myProductCode = productCode;
    myName = name;
    myPlatformPrefix = platformPrefix;
    myMavenCoordinates = mavenCoordinates;
    myCdnCoordinates = cdnCoordinates;
  }

  public static IntelliJPlatformProduct fromBuildNumber(String buildNumber) {
    for (IntelliJPlatformProduct product : values()) {
      if (buildNumber.startsWith(product.myProductCode)) {
        return product;
      }
    }
    return IDEA;
  }

  public static @Nullable IntelliJPlatformProduct fromProductCode(@Nullable String productCode) {
    return ContainerUtil.find(values(), product -> product.myProductCode.equals(productCode));
  }

  public static @Nullable IntelliJPlatformProduct fromMavenCoordinates(@NotNull String groupId, @NotNull String artifactId) {
    return ContainerUtil.find(values(), product -> Objects.equals(product.getMavenCoordinates(), groupId + ":" + artifactId));
  }

  public static @Nullable IntelliJPlatformProduct fromCdnCoordinates(@NotNull String groupId, @NotNull String artifactId) {
    return ContainerUtil.find(values(), product -> Objects.equals(product.getCdnCoordinates(), groupId + ":" + artifactId));
  }
}
