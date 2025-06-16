package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service

@Remote("org.jetbrains.kotlin.config.KotlinFacetSettingsProvider",
        plugin = "org.jetbrains.kotlin")
interface KotlinFacetSettingsProvider {
  fun getInstance(project: Project): KotlinFacetSettingsProvider
  fun getInitializedSettings(module: Module) : IKotlinFacetSettings
}

@Remote("org.jetbrains.kotlin.config.IKotlinFacetSettings",
        plugin = "org.jetbrains.kotlin")
interface IKotlinFacetSettings {
  fun getTargetPlatform(): List<TargetPlatform>
  fun getLanguageLevel(): LanguageVersion
  fun getApiLevel(): LanguageVersion
  fun getTestOutputPath(): String?
}


@Remote("org.jetbrains.kotlin.platform.TargetPlatform")
interface TargetPlatform

@Remote("org.jetbrains.kotlin.config.LanguageVersion",
        plugin = "org.jetbrains.kotlin")
interface LanguageVersion

fun Driver.getKotlinFacetSettings(project: Project? = null, module: Module) = service<KotlinFacetSettingsProvider>(project ?: singleProject())
  .getInitializedSettings(module)