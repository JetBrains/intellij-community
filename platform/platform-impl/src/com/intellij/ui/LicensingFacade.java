// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LicensingFacade {
  public String platformProductCode;
  public String licensedTo;
  public @NlsSafe String licenseeEmail;
  public List<String> restrictions;
  public boolean isEvaluation;
  public Date expirationDate;
  public Date perpetualFallbackDate;
  public Map<String, Date> expirationDates;
  public Map<String, String> confirmationStamps;
  public Map<String, ProductLicenseData> productLicenses;
  public String metadata;
  /** @deprecated use {@link #getInstance()} instead */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("StaticNonFinalField")
  public static volatile LicensingFacade INSTANCE;
  public boolean ai_enabled;

  @SuppressWarnings("StaticNonFinalField")
  public static volatile boolean isUnusedSignalled;
  /** @deprecated temporary field; use {@link #metadata} instead */
  @Deprecated(forRemoval = true)
  public String subType;

  public static @Nullable LicensingFacade getInstance() {
    return INSTANCE;
  }

  /**
   * @param productCode the product code to look up the expiration date for
   * @return the expiration date for the specified product as it is hard-coded in the license.
   * Normally, there is the last day when the license is still valid.
   * {@code null} value is returned if expiration date is not applicable for the product or the license has not been obtained.
   */
  public @Nullable Date getExpirationDate(String productCode) {
    final Map<String, Date> result = expirationDates;
    return result != null? result.get(productCode) : null;
  }

  public @Nullable String getLicensedToMessage() {
    return licensedTo;
  }

  public @NlsSafe @Nullable String getLicenseeEmail() {
    return licenseeEmail;
  }

  public @NotNull List<String> getLicenseRestrictionsMessages() {
    final List<String> result = restrictions;
    return result != null? result : Collections.emptyList();
  }

  public boolean isEvaluationLicense() {
    return isEvaluation;
  }

  public boolean isApplicableForProduct(@NotNull Date releaseDate) {
    final Date expDate = expirationDate;
    return isPerpetualForProduct(releaseDate) || (expDate == null || releaseDate.before(expDate));
  }

  public boolean isPerpetualForProduct(@NotNull Date releaseDate) {
    final Date result = perpetualFallbackDate;
    return result != null && releaseDate.before(result);
  }

  /**
   * @return the first day when the IDE license becomes invalid
   */
  public @Nullable Date getLicenseExpirationDate() {
    return expirationDate;
  }

  /**
   * @return a "confirmation stamp" string describing the license obtained by the licensing subsystem for the product
   * with the given productCode, or {@code null} if no license is currently obtained for the product.
   * <p>
   * A confirmation stamp is structured according to the following rules:
   * <pre>
   *   confirmationStamp := key:'license-key' | stamp:'license-server-stamp' | eval:'eval-key'
   *   <br>
   *   licenseKey := 'licenseId'-'licenseJsonBase64'-'signatureBase64'-'certificateBase64'<br>
   *     the signed part is licenseJson
   *   <br>
   *   license-server-stamp := 'timestampLong':'machineId':'signatureType':'signatureBase64':'certificateBase64'[:'intermediate-certificateBase64']<br>
   *     the signed part is 'timestampLong':'machineId' <br>
   *     machineId should be the same as {@link PermanentInstallationID#get()} returns
   *   <br>
   *   eval-key := 'expiration-date-long'
   * </pre>
   * @see <a href="https://plugins.jetbrains.com/docs/marketplace/add-marketplace-license-verification-calls-to-the-plugin-code.html">
   *   JetBrains Marketplace online documentation</a> for more information
   */
  public @Nullable String getConfirmationStamp(String productCode) {
    final Map<String, String> result = confirmationStamps;
    return result != null? result.get(productCode) : null;
  }

  @ApiStatus.Internal
  public static void setInstance(@Nullable LicensingFacade instance) {
    INSTANCE = instance;
    ApplicationManager.getApplication().getMessageBus().syncPublisher(LicenseStateListener.TOPIC).licenseStateChanged(instance);
  }

  private static @NotNull Gson createGson() {
    return new GsonBuilder().setDateFormat("yyyyMMdd").create();
  }

  public String toJson() {
    return createGson().toJson(this);
  }

  public static @Nullable LicensingFacade fromJson(String json) {
    try {
      return createGson().fromJson(json, LicensingFacade.class);
    }
    catch (Throwable e) {
      return null;
    }
  }

  public static void signalUnused(boolean value) {
    isUnusedSignalled = value;
  }

  public static final class ProductLicenseData {
    public String productCode;
    public @Nullable String confirmationStamp;
    public @Nullable Date expirationDate;
    public boolean isPersonal;
  }

  public interface LicenseStateListener extends EventListener {
    @NotNull Topic<LicenseStateListener> TOPIC = new Topic<>(LicenseStateListener.class);
    void licenseStateChanged(@Nullable LicensingFacade newState);
  }
}
