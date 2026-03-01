// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.CL_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.CL_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.CL_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.DG_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.DG_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.DG_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.DS_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.DS_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.DS_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.GO_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.GO_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.GO_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IC_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IC_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IC_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IU_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IU_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.IU_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.MPS_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.MPS_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.MPS_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PC_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PC_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PC_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PS_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PS_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PS_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PY_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PY_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.PY_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RD_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RD_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RD_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RM_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RM_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RM_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RR_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RR_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.RR_48
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.WS_20
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.WS_24
import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.WS_48
import com.intellij.ide.startup.importSettings.data.IconProductSize
import com.intellij.ide.startup.importSettings.jb.IDEData.Companion.IDE_MAP
import com.intellij.util.PlatformUtils
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.name

object NameMappings {
  fun getIcon(ideName: String, iconSize: IconProductSize): Icon? = when (iconSize) {
    IconProductSize.SMALL -> IDE_MAP[ideName]?.icon20
    IconProductSize.MIDDLE -> IDE_MAP[ideName]?.icon24
    IconProductSize.LARGE -> IDE_MAP[ideName]?.icon48
  }

  fun getFullName(ideName: String): String? = IDE_MAP[ideName]?.fullName

  fun canImportDirectly(prevIdeName: String): Boolean {
    val prevIdeData = IDE_MAP[prevIdeName] ?: return false
    val currentIdeData = IDEData.getSelf() ?: return false
    return prevIdeData == currentIdeData || supportedDirectImports.contains(Pair(prevIdeData, currentIdeData))
  }

  private val supportedDirectImports = listOf(
    Pair(IDEData.CLION, IDEData.CLION_NOVA),
    Pair(IDEData.CLION_NOVA, IDEData.CLION),
    Pair(IDEData.CLION, IDEData.RUSTROVER),
    Pair(IDEData.IDEA_COMMUNITY, IDEData.IDEA_ULTIMATE),
    Pair(IDEData.PYCHARM_CE, IDEData.PYCHARM)
  )
}

enum class IDEData(
  val code: String,
  val marketplaceCode: String,
  val folderName: String,
  val fullName: String,
  val icon20: Icon?,
  val icon24: Icon?,
  val icon48: Icon?,
) {
  CLION("CL", marketplaceCode = "clion", folderName = "CLion", fullName = "CLion", CL_20, CL_24, CL_48),
  CLION_NOVA("CL", marketplaceCode = "clion", folderName = "CLionNova", fullName = "CLion Nova", CL_20, CL_24, CL_48),
  DATAGRIP("DG", marketplaceCode = "dbe", folderName = "DataGrip", fullName = "DataGrip", DG_20, DG_24, DG_48),
  DATASPELL("DS", marketplaceCode = "dataspell", folderName = "DataSpell", fullName = "DataSpell", DS_20, DS_24, DS_48),
  GOLAND("GO", marketplaceCode = "go", folderName = "GoLand", fullName = "GoLand", GO_20, GO_24, GO_48),
  IDEA_COMMUNITY("IC", marketplaceCode = "idea_ce", folderName = "IdeaIC", fullName = "IntelliJ IDEA Community", IC_20, IC_24, IC_48),
  IDEA_ULTIMATE("IU", marketplaceCode = "idea", folderName = "IntelliJIdea", fullName = "IntelliJ IDEA", IU_20, IU_24, IU_48),
  MPS("MPS", marketplaceCode = "mps", folderName = "MPS", fullName = "MPS", MPS_20, MPS_24, MPS_48),
  PHPSTORM("PS", marketplaceCode = "phpstorm", folderName = "PhpStorm", fullName = "PhpStorm", PS_20, PS_24, PS_48),
  PYCHARM("PY", marketplaceCode = "pycharm", folderName = "PyCharm", fullName = "PyCharm", PY_20, PY_24, PY_48),
  PYCHARM_CE("PC", marketplaceCode = "pycharm_ce", folderName = "PyCharmCE", fullName = "PyCharm Community", PC_20, PC_24, PC_48),
  RIDER("RD", marketplaceCode = "rider", folderName = "Rider", fullName = "Rider", RD_20, RD_24, RD_48),
  RUBYMINE("RM", marketplaceCode = "ruby", folderName = "RubyMine", fullName = "RubyMine", RM_20, RM_24, RM_48),
  RUSTROVER("RR", marketplaceCode = "rust", folderName = "RustRover", fullName = "RustRover", RR_20, RR_24, RR_48),
  WEBSTORM("WS", marketplaceCode = "webstorm", folderName = "WebStorm", fullName = "WebStorm", WS_20, WS_24, WS_48),
  ;

  companion object {
    val IDE_MAP: Map<String, IDEData> = entries.associateBy { it.folderName }

    @Suppress("DEPRECATION")
    fun getSelf(): IDEData? = when {
      PlatformUtils.isCLion() -> CLION
      PlatformUtils.isDataGrip() -> DATAGRIP
      PlatformUtils.isDataSpell() -> DATASPELL
      PlatformUtils.isGoIde() -> GOLAND
      PlatformUtils.isIdeaCommunity() -> IDEA_COMMUNITY
      PlatformUtils.isIdeaUltimate() -> IDEA_ULTIMATE
      PlatformUtils.isMPS() -> MPS
      PlatformUtils.isPhpStorm() -> PHPSTORM
      PlatformUtils.isPyCharmCommunity() -> PYCHARM_CE
      PlatformUtils.isPyCharmPro() -> PYCHARM
      PlatformUtils.isRider() -> RIDER
      PlatformUtils.isRubyMine() -> RUBYMINE
      PlatformUtils.isRustRover() -> RUSTROVER
      PlatformUtils.isWebStorm() -> WEBSTORM
      else -> null
    }

    fun getForConfigDir(configDir: Path): IDEData? {
      return IDEData.IDE_MAP
        .filter { configDir.name.startsWith(it.key) }
        .maxByOrNull { it.key.length }
        ?.value
    }
  }
}
