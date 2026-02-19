// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.provisioner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface ProvisionerCompanyBrandingProvider{
  val companyBranding: Flow<CompanyBranding>

  @ApiStatus.Experimental
  fun getCurrentEnterpriseState(): CompanyBranding

  companion object {
    fun getInstance(): ProvisionerCompanyBrandingProvider = ApplicationManager.getApplication().service()
  }
}

internal class DefaultProvisionerCompanyBrandingProvider: ProvisionerCompanyBrandingProvider {
  override val companyBranding = flowOf(CompanyBranding.NotProvisioned)
  override fun getCurrentEnterpriseState(): CompanyBranding = CompanyBranding.NotProvisioned
}

sealed class CompanyBranding {
  data object NotReady: CompanyBranding()
  data class Provisioned(val info: EnterpriseInfo): CompanyBranding()
  data object NotProvisioned: CompanyBranding()
}

data class EnterpriseInfo(
  val logo: Icon,
  val companyName: @NlsSafe String,
  val browserUrl: String,
)
