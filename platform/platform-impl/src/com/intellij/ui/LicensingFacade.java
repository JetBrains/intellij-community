// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.PermanentInstallationID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class LicensingFacade {
  public String licensedTo;
  public String licenseeEmail;
  public List<String> restrictions;
  public boolean isEvaluation;
  public Date expirationDate;
  public Date perpetualFallbackDate;
  public Map<String, Date> expirationDates;
  public Map<String, String> confirmationStamps;
  public String metadata;

  public volatile static LicensingFacade INSTANCE;

  @Nullable
  public static LicensingFacade getInstance() {
    return INSTANCE;
  }

  @Nullable
  public String getLicensedToMessage() {
    return licensedTo;
  }

  @Nullable
  public String getLicenseeEmail() {
    return licenseeEmail;
  }

  @NotNull
  public List<String> getLicenseRestrictionsMessages() {
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
  @Nullable
  public Date getLicenseExpirationDate() {
    return expirationDate;
  }

  /**
   * @param productCode the product code to lookup the expiration date for
   * @return the expiration date for the specified product as it is hard-coded in the license.
   * Normally the is the last day when the license is still valid.
   * null value is returned if expiration date is not applicable for the product, or the licence has net been obtained
   */
  @Nullable
  public Date getExpirationDate(String productCode) {
    final Map<String, Date> result = expirationDates;
    return result != null? result.get(productCode) : null;
  }

  /**
   * @param productCode
   * @return a "confirmation stamp" string describing the license obtained by the licensing subsystem for the product with the given productCode.
   *  returns null, if no license is currently obtained for the product.
   *
   *  A confirmation stamp is structured according to the following rules:
   *  <pre>
   *  confirmationStamp := key:'license-key' | stamp:'license-server-stamp' | eval:'eval-key'
   *  <br><br>
   *  licenseKey := 'licenseId'-'licenseJsonBase64'-'signatureBase64'-'certificateBase64'  <br>
   *    the signed part is licenseJson
   *  <br><br>
   *  license-server-stamp := 'timestampLong':'machineId':'signatureType':'signatureBase64':'certificateBase64'[:'intermediate-certificateBase64']
   *  <br>
   *    the signed part is 'timestampLong':'machineId' <br>
   *    machineId should be the same as {@link PermanentInstallationID#get()} returns
   *   <br><br>
   *  eval-key := 'expiration-date-long'
   *
   * @see <a href="https://plugins.jetbrains.com/docs/marketplace/add-marketplace-license-verification-calls-to-the-plugin-code.html">JetBrains Marketplace online documentation</a> for more information
   *  </pre>
   */
  @Nullable
  public String getConfirmationStamp(String productCode) {
    final Map<String, String> result = confirmationStamps;
    return result != null? result.get(productCode) : null;
  }

  private static @NotNull Gson createGson() {
    return new GsonBuilder().setDateFormat("yyyyMMdd").create();
  }

  public String toJson() {
    return createGson().toJson(this);
  }

  @Nullable
  public static LicensingFacade fromJson(String json) {
    try {
      return createGson().fromJson(json, LicensingFacade.class);
    }
    catch (Throwable e) {
      return null;
    }
  }
}