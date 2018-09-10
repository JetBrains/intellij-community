// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

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
  public Map<String, String> confirmationStamps;

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

  @Nullable
  public Date getLicenseExpirationDate() {
    return expirationDate;
  }

  @Nullable
  public String getConfirmationStamp(String productCode) {
    return confirmationStamps == null? null : confirmationStamps.get(productCode);
  }
}