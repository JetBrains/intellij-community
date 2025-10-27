// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.xml.dom.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.application.impl.UtilKt.withTempConfigDirectory;
import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationInfoTest {
  @Test
  public void shortenCompanyName() {
    assertThat(createAppInfo(new XmlElement("company", Map.of("name", "Google Inc."), List.of(), null)).getShortCompanyName()).isEqualTo("Google");
    assertThat(createAppInfo(new XmlElement("company", Map.of("name", "JetBrains s.r.o."), List.of(), null)).getShortCompanyName()).isEqualTo("JetBrains");
    assertThat(createAppInfo(new XmlElement("company", Map.of("shortName", "Acme Inc."), List.of(), null)).getShortCompanyName()).isEqualTo("Acme Inc.");
  }

  @Test
  public void pluginsHostProperty() {
    var host = "IntellijIdeaRulezzz";
    PlatformTestUtil.withSystemProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY, host, () -> {
      var info = createAppInfo();
      assertThat(info.getPluginManagerUrl()).contains(host).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getPluginsListUrl()).contains(host).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getPluginDownloadUrl()).contains(host).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
    });
  }

  @Test
  public void splashImage_eapLogoPreferredInEap() {
    ApplicationInfo info = createAppInfo(eap(true), logo, logoEap);
    assertThat(info.isEAP()).isTrue();
    String url = info.getSplashImageUrl();
    assertThat(url).isEqualTo(logoEapUrl);
  }

  @Test
  public void splashImage_regularLogoWhenNotEap() {
    ApplicationInfo info = createAppInfo(eap(false), logo, logoEap);
    assertThat(info.isEAP()).isFalse();
    String url = info.getSplashImageUrl();
    assertThat(url).isEqualTo(logoUrl);
  }

  @Test
  public void splashImage_regularLogoInEapWithNoEapLogoProvided() {
    ApplicationInfo info = createAppInfo(eap(true), logo);
    assertThat(info.isEAP()).isTrue();
    String url = info.getSplashImageUrl();
    assertThat(url).isEqualTo(logoUrl);
  }

  @Test
  public void splashImage_subscriptionModeLogoPreferredWhenMarkerPresent() {
    withTempConfigDirectory(configDir -> {
      createSubscriptionModeSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(logo, logoSubscriptionMode);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoSubscriptionModeUrl);
    });
  }

  @Test
  public void splashImage_simplifiedLogoPreferredWhenMarkerPresent() {
    withTempConfigDirectory(configDir -> {
      createSimplifiedSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(logo, logoSimplified);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoSimplifiedUrl);
    });
  }

  @Test
  public void splashImage_regularLogoWhenNoSubscriptionModeMarker() {
    withTempConfigDirectory(configDir -> {
      deleteSubscriptionModeSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(logo, logoSubscriptionMode);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoUrl);
    });
  }

  @Test
  public void splashImage_regularLogoWhenNoSimplifiedMarker() {
    withTempConfigDirectory(configDir -> {
      deleteSimplifiedSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(logo, logoSimplified);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoUrl);
    });
  }

  @Test
  public void splashImage_regularLogoWhenMarkerPresentButNoSubscriptionModeLogoProvided() {
    withTempConfigDirectory(configDir -> {
      createSubscriptionModeSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(logo);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoUrl);
    });
  }

  @Test
  public void splashImage_regularLogoWhenMarkerPresentButNoSimplifiedLogoProvided() {
    withTempConfigDirectory(configDir -> {
      createSimplifiedSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(logo);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoUrl);
    });
  }

  @Test
  public void splashImage_eapLogoBeatsSubscriptionModeInEapWhenBothAvailableAndMarkerPresent() {
    withTempConfigDirectory(configDir -> {
      createSubscriptionModeSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(eap(true), logo, logoEap, logoSubscriptionMode);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoEapUrl);
    });
  }

  @Test
  public void splashImage_eapLogoBeatsSimplifiedInEapWhenBothAvailableAndMarkerPresent() {
    withTempConfigDirectory(configDir -> {
      createSimplifiedSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(eap(true), logo, logoEap, logoSimplified);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoEapUrl);
    });
  }

  @Test
  public void splashImage_subscriptionModeLogoWhenNotEapAndMarkerPresent() {
    withTempConfigDirectory(configDir -> {
      createSubscriptionModeSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(eap(false), logo, logoEap, logoSubscriptionMode);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoSubscriptionModeUrl);
    });
  }

  @Test
  public void splashImage_simplifiedLogoWhenNotEapAndMarkerPresent() {
    withTempConfigDirectory(configDir -> {
      createSimplifiedSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(eap(false), logo, logoEap, logoSimplified);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoSimplifiedUrl);
    });
  }

  @Test
  public void splashImage_regularLogoWhenNotEapAndNoMarker() {
    withTempConfigDirectory(configDir -> {
      deleteSubscriptionModeSplashMarkerFile(configDir);
      deleteSimplifiedSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(eap(false), logo, logoEap, logoSubscriptionMode);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoUrl);
    });
  }

  @Test
  public void splashImage_simplifiedLogoBeatsSubscriptionModeWhenBothAvailableAndMarkersPresent() {
    withTempConfigDirectory(configDir -> {
      createSubscriptionModeSplashMarkerFile(configDir);
      createSimplifiedSplashMarkerFile(configDir);
      ApplicationInfo info = createAppInfo(eap(false), logo, logoSubscriptionMode, logoSimplified);
      String url = info.getSplashImageUrl();
      assertThat(url).isEqualTo(logoSimplifiedUrl);
    });
  }

  @Test
  public void simplifiedSplashIsSupportedIfThereIsAnImageDefined() {
    var info = createAppInfo(logo, logoSimplified);
    assertThat(info.isSimplifiedSplashSupported()).isTrue();
  }

  @Test
  public void simplifiedSplashIsNotSupportedIfThereIsNoImageDefined() {
    var info = createAppInfo(logo, logoEap);
    assertThat(info.isSimplifiedSplashSupported()).isFalse();
  }

  public static @NotNull ApplicationInfoImpl createAppInfo(@NotNull XmlElement @NotNull ... content) {
    var children = new ArrayList<>(List.of(content));
    children.add(new XmlElement("icon", Map.of("svg", "xxx.svg", "svg-small", "xxx.svg"), List.of(), null));
    children.add(new XmlElement("plugins", Map.of(), List.of(), null));
    return new ApplicationInfoImpl(new XmlElement("state", Map.of(), children, null));
  }

  private static void createSubscriptionModeSplashMarkerFile(@NotNull Path configDir) throws IOException {
    Files.writeString(configDir.resolve(ApplicationInfoImpl.SUBSCRIPTION_MODE_SPLASH_MARKER_FILE_NAME), "");
  }

  private static void createSimplifiedSplashMarkerFile(@NotNull Path configDir) throws IOException {
    Files.writeString(configDir.resolve(ApplicationInfoImpl.SIMPLIFIED_SPLASH_MARKER_FILE_NAME), "");
  }

  private static void deleteSubscriptionModeSplashMarkerFile(@NotNull Path configDir) throws IOException {
    Files.deleteIfExists(configDir.resolve(ApplicationInfoImpl.SUBSCRIPTION_MODE_SPLASH_MARKER_FILE_NAME));
  }

  private static void deleteSimplifiedSplashMarkerFile(@NotNull Path configDir) throws IOException {
    Files.deleteIfExists(configDir.resolve(ApplicationInfoImpl.SIMPLIFIED_SPLASH_MARKER_FILE_NAME));
  }

  private static XmlElement eap(boolean isEap) {
    return new XmlElement("version", Map.of("eap", String.valueOf(isEap)), List.of(), null);
  }

  private static final String logoUrl = "/logo.png";
  private static final String logoEapUrl = "/logo_eap.png";
  private static final String logoSimplifiedUrl = "/logo_simplified.png";
  private static final String logoSubscriptionModeUrl = "/logo_subscription.png";

  private final XmlElement logo = new XmlElement("logo", Map.of("url", logoUrl), List.of(), null);
  private final XmlElement logoEap = new XmlElement("logo-eap", Map.of("url", logoEapUrl), List.of(), null);
  private final XmlElement logoSimplified = new XmlElement("logo-simplified", Map.of("url", logoSimplifiedUrl), List.of(), null);
  private final XmlElement logoSubscriptionMode = new XmlElement("logo-subscription-mode", Map.of("url", logoSubscriptionModeUrl), List.of(), null);
}
