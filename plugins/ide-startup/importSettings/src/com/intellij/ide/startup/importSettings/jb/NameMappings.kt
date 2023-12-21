// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.ide.startup.importSettings.StartupImportIcons.IdeIcons.*
import com.intellij.ide.startup.importSettings.data.IconProductSize
import com.intellij.ide.startup.importSettings.jb.IDEData.Companion.IDE_MAP
import com.intellij.util.PlatformUtils
import javax.swing.Icon

object NameMappings {

  fun getIcon(ideName: String, iconSize: IconProductSize): Icon? {
    when (iconSize) {
      IconProductSize.SMALL -> {
        return IDE_MAP[ideName]?.icon20
      }
      IconProductSize.MIDDLE -> {
        return IDE_MAP[ideName]?.icon24
      }
      IconProductSize.LARGE -> {
        return IDE_MAP[ideName]?.icon48
      }
      else -> {
        return null
      }
    }
  }

  fun getFullName(ideName: String) : String {
    return IDE_MAP[ideName]?.fullName ?: ideName
  }

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

private enum class IDEData(val code: String,
                           val folderName: String,
                           val fullName: String,
                           val icon20: Icon?,
                           val icon24: Icon?,
                           val icon48: Icon?
                            ) {
  APPCODE("AC", "AppCode", "AppCode", AC_20, AC_24, AC_48),
  AQUA("Aqua", "Aqua", "Aqua", Aqua_20, Aqua_24, Aqua_48),
  CLION("CL", "CLion", "CLion", CL_20, CL_24, CL_48),
  CLION_NOVA("CL", "CLionNova", "CLion Nova", CL_20, CL_24, CL_48),
  DATAGRIP("DG", "DataGrip", "DataGrip", DG_20, DG_24, DG_48),
  DATASPELL("DS", "DataSpell", "DataSpell", DS_20, DS_24, DS_48),
  GOLAND("GO", "GoLand", "GoLand", GO_20, GO_24, GO_48),
  IDEA_COMMUNITY("IC","IdeaIC", "IntelliJ IDEA Community", IC_20, IC_24, IC_48),
  IDEA_ULTIMATE("IU", "IntelliJIdea", "IntelliJ IDEA Ultimate", IU_20, IU_24,IU_48),
  MPS("MPS", "MPS", "MPS", MPS_20, MPS_24, MPS_48),
  PYCHARM("PY", "PyCharm", "PyCharm Professional", PY_20, PY_24, PY_48),
  PYCHARM_CE("PC", "PyCharmCE", "PyCharm Community", PC_20, PC_24, PC_48),
  PHPSTORM("PS", "PhpStorm", "PhpStorm", PS_20, PS_24, PS_48),
  RIDER("RD", "Rider", "Rider", RD_20, RD_24, RD_48),
  RUBYMINE("RM", "RubyMine", "RubyMine", RM_20, RM_24, RM_48),
  RUSTROVER("RR", "RustRover", "RustRover", RR_20, RR_24, RR_48),
  WEBSTORM("WS", "WebStorm", "WebStorm", WS_20, WS_24, WS_48)
  ;

  companion object {
    val IDE_MAP = entries.associateBy { it.folderName }

    @Suppress("DEPRECATION")
    fun getSelf(): IDEData? = when {
      PlatformUtils.isAppCode() -> APPCODE
      PlatformUtils.isAqua() -> AQUA
      PlatformUtils.isCLion() -> CLION
      PlatformUtils.isDataGrip() -> DATAGRIP
      PlatformUtils.isDataSpell() -> DATASPELL
      PlatformUtils.isGoIde() -> GOLAND
      PlatformUtils.isIdeaCommunity() -> IDEA_COMMUNITY
      PlatformUtils.isIdeaUltimate() -> IDEA_ULTIMATE
      PlatformUtils.isPhpStorm() -> PHPSTORM
      PlatformUtils.isPyCharmCommunity() -> PYCHARM_CE
      PlatformUtils.isPyCharmPro() -> PYCHARM
      PlatformUtils.isRider() -> RIDER
      PlatformUtils.isRubyMine() -> RUBYMINE
      PlatformUtils.isRustRover() -> RUSTROVER
      PlatformUtils.isWebStorm() -> WEBSTORM
      else -> {
        null
      }
    }
  }
}