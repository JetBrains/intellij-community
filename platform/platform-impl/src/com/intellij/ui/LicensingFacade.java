// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class LicensingFacade {
  public String licensedTo;
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

  @NotNull
  public List<String> getLicenseRestrictionsMessages() {
    return restrictions == null? Collections.emptyList() : Collections.unmodifiableList(restrictions);
  }

  public boolean isEvaluationLicense() {
    return isEvaluation;
  }

  public boolean isApplicableForProduct(@NotNull Date releaseDate) {
    return isPerpetualForProduct(releaseDate) || (expirationDate == null || releaseDate.before(expirationDate));
  }

  public boolean isPerpetualForProduct(@NotNull Date releaseDate) {
    return perpetualFallbackDate != null && releaseDate.before(perpetualFallbackDate);
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
    return expirationDates == null ? null : expirationDates.get(productCode);
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
   *  licenseKey := 'licensId'-'licenseJsonBase64'-'signatureBase64'-'certificateBase64'  <br>
   *    the signed part is licenseJson
   *  <br><br>
   *  license-server-stamp := 'timestampLong':'machineId':'signatureType':'signatureBase64':'certificateBase64'[:'intermediate-certificateBase64']
   *  <br>
   *    the signed part is 'timestampLong':'machineId' <br>
   *    machineId should be the same as {@link PermanentInstallationID#get()} returns
   *   <br><br>
   *  eval-key := 'expiration-date-long'
   *  </pre>
   */
  @Nullable
  public String getConfirmationStamp(String productCode) {
    return confirmationStamps == null? null : confirmationStamps.get(productCode);
  }

  public boolean isEA2Product(@NotNull String productCodeOrPluginId) {
    return ArrayUtil.contains(productCodeOrPluginId, "DPN", "DC", "DPA", "PDB", "PWS", "PGO", "PPS", "PPC", "PRB", "PSW", "Pythonid");
  }
}